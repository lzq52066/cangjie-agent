package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.SpecialToolRoute;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolGateway;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
import cn.cangjiecloud.tool.util.ToolNaming;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具网关实现 —— Agent 循环里唯一的工具执行入口。
 * <p>
 * 流程：特殊路由匹配 → 工具元信息预解析（供钩子判定）→ 钩子链（校验/策略/审批）→ 带超时执行
 * → 输出截断 → 后置钩子。整体不抛异常，一切失败都以 {@link ToolStatus} 表达，
 * 使模型能读到错误并自行改道，而不会打断整轮对话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGatewayImpl implements ToolGateway {

    /** 与改造前一致：日志里的参数/结果摘要截断长度 */
    private static final int LOG_ABBREV_CHARS = 200;

    private final IToolService toolService;
    private final HarnessConfigResolver configResolver;
    private final List<ToolCallHook> hookBeans;
    private final List<SpecialToolRoute> routeBeans;

    /** 钩子链与路由按 order 升序固化，避免每次调用重复排序 */
    private List<ToolCallHook> hooks = List.of();
    private List<SpecialToolRoute> routes = List.of();

    /** 仅用于施加单次调用超时；串行语义不变，不会并发执行同一轮的多个工具 */
    private ExecutorService toolExecutor;

    @PostConstruct
    void init() {
        hooks = new ArrayList<>(hookBeans);
        hooks.sort(Comparator.comparingInt(ToolCallHook::getOrder));
        routes = new ArrayList<>(routeBeans);
        routes.sort(Comparator.comparingInt(SpecialToolRoute::getOrder));
        toolExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "harness-tool");
            thread.setDaemon(true);
            return thread;
        });
        log.info("工具网关已装配: 钩子={}, 特殊路由={}",
                hooks.stream().map(h -> h.getClass().getSimpleName()).toList(),
                routes.stream().map(SpecialToolRoute::name).toList());
    }

    @PreDestroy
    void destroy() {
        if (toolExecutor != null) {
            toolExecutor.shutdownNow();
        }
    }

    @Override
    public List<ToolSpecification> resolveSpecs(List<String> toolIds) {
        return toolService.getToolSpecifications(toolIds);
    }

    @Override
    public ToolOutcome invoke(ToolInvocation invocation, HarnessContext context) {
        ToolOutcome outcome = dispatch(invocation, context);
        for (ToolCallHook hook : hooks) {
            try {
                hook.after(invocation, outcome, context);
            } catch (Exception e) {
                log.warn("工具后置钩子异常已忽略: hook={}, {}", hook.getClass().getSimpleName(), e.getMessage());
            }
        }
        return outcome;
    }

    // ==================== 前置判定 ====================

    private ToolOutcome dispatch(ToolInvocation invocation, HarnessContext context) {
        long startMs = System.currentTimeMillis();
        SpecialToolRoute route = matchRoute(invocation, context);
        if (route == null) {
            resolveMetadata(invocation);
        } else if (!StringUtils.hasText(invocation.getToolType())) {
            invocation.setToolType(route.name());
        }

        // 本地工具服务端不执行：不走钩子链（无需审批/策略），直接挂起交给调用方环境（浏览器）执行
        if (ToolConstants.ToolType.LOCAL.equals(invocation.getToolType())) {
            return ToolOutcome.waitingLocal();
        }

        for (ToolCallHook hook : hooks) {
            ToolCallHook.Decision decision;
            try {
                decision = hook.before(invocation, context);
            } catch (Exception e) {
                log.error("工具前置钩子异常，按放行处理: hook={}, tool={}",
                        hook.getClass().getSimpleName(), invocation.getCallName(), e);
                continue;
            }
            if (decision == null || decision.isProceed()) {
                continue;
            }
            if (decision.isRequireApproval()) {
                if (context.isApproved(invocation.getCallId())) {
                    continue;
                }
                return ToolOutcome.waitingApproval(decision.getReason(), decision.getRiskLevel());
            }
            return ToolOutcome.denied(decision.getReason());
        }
        return execute(invocation, context, route, startMs);
    }

    /**
     * 工具元信息预解析：让钩子只依赖 {@link ToolInvocation} 就能判定风险、审批与超时，
     * 不必各自回查数据库。解析失败（含技能前缀）时保持为空，走全局默认。
     */
    private void resolveMetadata(ToolInvocation invocation) {
        String callName = invocation.getCallName();
        if (!StringUtils.hasText(callName) || callName.startsWith(ToolNaming.SKILL_PREFIX)) {
            return;
        }
        try {
            ToolEntity entity = toolService.resolveByCallName(callName);
            if (entity == null) {
                return;
            }
            invocation.setToolId(entity.getId());
            invocation.setToolType(StringUtils.hasText(entity.getToolType())
                    ? entity.getToolType() : entity.getType());
            invocation.setRiskLevel(entity.getRiskLevel());
            invocation.setRequireApproval(Integer.valueOf(1).equals(entity.getRequireApproval()));
            invocation.setTimeoutSeconds(entity.getTimeoutSeconds());
            invocation.setMaxOutputChars(entity.getMaxOutputChars());
        } catch (Exception e) {
            log.warn("工具元信息解析失败，按默认策略执行: tool={}, {}", callName, e.getMessage());
        }
    }

    private SpecialToolRoute matchRoute(ToolInvocation invocation, HarnessContext context) {
        for (SpecialToolRoute route : routes) {
            try {
                if (route.matches(invocation, context)) {
                    return route;
                }
            } catch (Exception e) {
                log.warn("特殊工具路由匹配异常: route={}, {}", route.name(), e.getMessage());
            }
        }
        return null;
    }

    // ==================== 执行 ====================

    private ToolOutcome execute(ToolInvocation invocation, HarnessContext context,
                                SpecialToolRoute route, long startMs) {
        long timeoutSeconds = timeoutSeconds(invocation, context);
        String raw;
        try {
            raw = timeoutSeconds > 0
                    ? callWithTimeout(invocation, context, route, timeoutSeconds)
                    : callDirectly(invocation, context, route);
        } catch (TimeoutException e) {
            long cost = System.currentTimeMillis() - startMs;
            log.warn("工具执行超时: tool={}, 限时={}s, 耗时={}ms", invocation.getCallName(), timeoutSeconds, cost);
            return ToolOutcome.timeout("工具执行超时，限时 " + timeoutSeconds + " 秒", cost);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolOutcome.failed("工具执行被中断", System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startMs;
            log.error("工具执行异常: tool={}", invocation.getCallName(), e);
            return ToolOutcome.failed(rootMessage(e), cost);
        }
        long cost = System.currentTimeMillis() - startMs;
        log.info("Function Calling 执行: {} -> args={}, result={}", invocation.getCallName(),
                abbrev(invocation.getArgumentsJson()), abbrev(raw));
        return toOutcome(invocation, raw, cost);
    }

    private long timeoutSeconds(ToolInvocation invocation, HarnessContext context) {
        if (invocation.getTimeoutSeconds() != null && invocation.getTimeoutSeconds() > 0) {
            return invocation.getTimeoutSeconds();
        }
        LoopPolicy policy = context.getRequest() == null ? null : context.getRequest().getLoopPolicy();
        long configured = policy == null ? 0 : policy.getToolTimeoutSeconds();
        return configured > 0 ? configured : 0;
    }

    /**
     * 超时执行会把工具调用切到工作线程，需要显式搬运链路上下文，
     * 否则工具内部的日志与埋点会丢 traceId。
     */
    private String callWithTimeout(ToolInvocation invocation, HarnessContext context,
                                   SpecialToolRoute route, long timeoutSeconds)
            throws Exception {
        String traceId = TraceContext.getTraceId();
        String sessionId = TraceContext.getSessionId();
        String userId = TraceContext.getUserId();
        Future<String> future = toolExecutor.submit(() -> {
            try {
                TraceContext.setTraceId(traceId);
                TraceContext.setSessionId(sessionId);
                TraceContext.setUserId(userId);
                return callDirectly(invocation, context, route);
            } finally {
                TraceContext.clear();
            }
        });
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private String callDirectly(ToolInvocation invocation, HarnessContext context, SpecialToolRoute route) {
        if (route != null) {
            return route.execute(invocation, context);
        }
        return toolService.executeToolCall(invocation.getCallName(), invocation.getArguments());
    }

    /**
     * 结果分类：工具侧返回的正文原样回喂模型（含 {@code {"success":false}} 结构），
     * 网关只负责识别失败态与按阈值截断，不重新包装错误体。
     */
    private ToolOutcome toOutcome(ToolInvocation invocation, String raw, long durationMs) {
        String text = raw == null ? "" : raw;
        ToolStatus status = ToolStatus.SUCCESS;
        String error = null;
        JSONObject json = asJsonObject(text);
        if (json != null && json.containsKey("success") && !json.getBooleanValue("success")) {
            status = ToolStatus.FAILED;
            error = json.getString("error");
        }
        int originalLength = text.length();
        boolean truncated = false;
        int limit = invocation.getMaxOutputChars() != null
                ? invocation.getMaxOutputChars() : configResolver.maxToolOutputChars();
        if (limit > 0 && originalLength > limit) {
            text = text.substring(0, limit) + "\n...(工具输出已截断，原始长度 " + originalLength + " 字符)";
            truncated = true;
        }
        ToolOutcome outcome = ToolOutcome.builder()
                .status(status)
                .output(text)
                .error(error)
                .durationMs(durationMs)
                .truncated(truncated)
                .riskLevel(invocation.getRiskLevel())
                .build();
        outcome.setModelContent("工具调用结果(" + invocation.getCallName() + "): " + text);
        return outcome;
    }

    private JSONObject asJsonObject(String text) {
        if (text == null || !text.startsWith("{")) {
            return null;
        }
        try {
            return JSON.parseObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    private String abbrev(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= LOG_ABBREV_CHARS ? text : text.substring(0, LOG_ABBREV_CHARS) + "...";
    }
}
