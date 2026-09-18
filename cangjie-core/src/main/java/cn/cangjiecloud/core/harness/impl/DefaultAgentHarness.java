package cn.cangjiecloud.core.harness.impl;

import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.core.harness.AssistantTurn;
import cn.cangjiecloud.core.harness.DeltaSink;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessException;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.HarnessTimeoutException;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.ModelGateway;
import cn.cangjiecloud.core.harness.ResumeResolver;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.ToolGateway;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Agent Harness 默认实现 —— 纯循环引擎，不感知 SSE、数据库与业务模块。
 * <p>
 * 一次 {@code run} 的生命周期：startRun → [轮次: 模型交换 → 工具执行/挂起] → finishRun。
 * 需要人工审批时不落线程，而是写检查点后以 {@link RunStatus#WAITING_APPROVAL} 返回，
 * 由 {@code resume} 在另一次请求中续跑。
 */
public class DefaultAgentHarness implements AgentHarness {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentHarness.class);

    private static final long DEFAULT_APPROVAL_TIMEOUT_SECONDS = 1800L;

    private final ModelGateway modelGateway;
    private final ToolGateway toolGateway;
    private final AgentRunRecorder recorder;
    private final ApprovalStore approvalStore;
    private final ResumeResolver resumeResolver;

    public DefaultAgentHarness(ModelGateway modelGateway, ToolGateway toolGateway,
                               AgentRunRecorder recorder, ApprovalStore approvalStore,
                               ResumeResolver resumeResolver) {
        this.modelGateway = modelGateway;
        this.toolGateway = toolGateway;
        this.recorder = recorder;
        this.approvalStore = approvalStore;
        this.resumeResolver = resumeResolver;
    }

    @Override
    public HarnessOutcome run(HarnessRequest request, HarnessListener listener) {
        HarnessListener l = listener == null ? HarnessListener.NOOP : listener;
        HarnessContext ctx = request.getResume() != null
                ? HarnessContext.restore(request, request.getResume())
                : new HarnessContext(request);
        if (ctx.getRunId() == null) {
            ctx.setRunId(recorder.startRun(request));
        }
        recordContext(request, ctx.getRunId(), l);
        return loop(ctx, l);
    }

    @Override
    public HarnessOutcome resume(String runId, boolean approved, String decidedBy, String remark,
                                 String resumeToken, HarnessListener listener) {
        HarnessListener l = listener == null ? HarnessListener.NOOP : listener;
        AgentRunRecorder.PausedRun paused = recorder.loadPaused(runId, resumeToken);
        if (paused == null || paused.getState() == null) {
            throw new HarnessException("运行不存在、已终结或恢复令牌无效: " + runId);
        }
        if (resumeResolver == null) {
            throw new HarnessException("未配置 ResumeResolver，无法恢复运行: " + runId);
        }
        HarnessRequest request = resumeResolver.resolve(paused, approved, remark);
        HarnessContext ctx = restoreContext(paused, request, l);
        LoopPolicy policy = request.getLoopPolicy() == null
                ? LoopPolicy.builder().build() : request.getLoopPolicy();
        if (!approved) {
            denyPendingCalls(ctx, remark);
        } else {
            // 放行本次挂起对应的那一个调用，避免恢复后审批钩子再次挂起
            ChatResponse.ToolCall call = ctx.currentPendingCall();
            if (call != null) {
                ctx.approve(call.getId());
            }
            // 先续跑挂起点之后的工具：检查点里最后一条是带 tool_calls 的 assistant 消息，
            // 必须补齐对应的 tool 结果再请求模型，否则消息序列非法。
            // 若续跑中又遇审批/本地工具挂起，直接返回新的挂起态。
            HarnessOutcome suspended = executePendingTools(ctx, policy, l);
            if (suspended != null) {
                return suspended;
            }
        }
        log.info("恢复 Agent 运行: runId={}, approved={}, 待执行工具={}",
                runId, approved, ctx.getPendingToolCalls().size() - ctx.getPendingIndex());
        return loop(ctx, l);
    }

    @Override
    public HarnessOutcome completeLocalTool(String runId, String callId, String resultJson, boolean failed,
                                            String resumeToken, HarnessListener listener) {
        HarnessListener l = listener == null ? HarnessListener.NOOP : listener;
        AgentRunRecorder.PausedRun paused = recorder.loadPaused(runId, resumeToken);
        if (paused == null || paused.getState() == null) {
            throw new HarnessException("运行不存在、已终结或恢复令牌无效: " + runId);
        }
        if (resumeResolver == null) {
            throw new HarnessException("未配置 ResumeResolver，无法恢复运行: " + runId);
        }
        HarnessRequest request = resumeResolver.resolve(paused, true, null);
        HarnessContext ctx = restoreContext(paused, request, l);

        ChatResponse.ToolCall call = ctx.currentPendingCall();
        ToolInvocation invocation = ToolInvocation.builder()
                .runId(ctx.getRunId())
                .callId(call == null ? callId : call.getId())
                .callName(call == null ? null : call.getName())
                .argumentsJson(call == null ? null : call.getArguments())
                .arguments(call == null ? Map.of() : parseArguments(call))
                .round(ctx.getRound())
                .stepNo(ctx.nextStepNo())
                .build();
        ToolOutcome outcome = failed
                ? ToolOutcome.failed(resultJson, 0L)
                : ToolOutcome.success(resultJson == null ? "" : resultJson, 0L);
        ctx.addToolResult(invocation, outcome);
        // 挂起那次只留了 waiting_local 步骤，这里补记浏览器侧回传的结果，保证 run 回放完整
        recordTool(ctx, invocation, outcome, System.currentTimeMillis(), System.currentTimeMillis());
        ctx.advancePendingIndex();
        // 续跑同一轮剩余的工具调用（模型一轮可能返回多个 tool_call）：
        // 直接 loop 会在仍缺这些 tool 结果时请求模型，导致非法消息序列。
        LoopPolicy policy = request.getLoopPolicy() == null
                ? LoopPolicy.builder().build() : request.getLoopPolicy();
        HarnessOutcome suspended = executePendingTools(ctx, policy, l);
        if (suspended != null) {
            return suspended;
        }
        log.info("本地工具结果已回传，恢复 Agent 运行: runId={}, callId={}, failed={}", runId, callId, failed);
        return loop(ctx, l);
    }

    private HarnessContext restoreContext(AgentRunRecorder.PausedRun paused, HarnessRequest request,
                                          HarnessListener listener) {
        HarnessContext ctx = HarnessContext.restore(request, paused.getState());
        recordContext(request, ctx.getRunId(), listener);
        return ctx;
    }

    // ==================== 主循环 ====================

    private HarnessOutcome loop(HarnessContext ctx, HarnessListener listener) {
        HarnessRequest request = ctx.getRequest();
        LoopPolicy policy = request.getLoopPolicy() == null
                ? LoopPolicy.builder().build() : request.getLoopPolicy();
        boolean toolsEnabled = request.getTools() != null && !request.getTools().isEmpty();
        DeltaSink sink = request.isStream() ? listener::onDelta : null;
        int promptCursor = 0;
        AssistantTurn lastTurn = null;

        try {
            while (true) {
                if (ctx.isCancelled()) {
                    // 客户端断开：保留已产出的部分内容，让业务侧按既有语义落库
                    if (ctx.getFinalText() == null && lastTurn != null) {
                        ctx.setFinalText(lastTurn.getContent());
                    }
                    return finish(ctx, RunStatus.CANCELLED, listener);
                }
                if (policy.deadlineExceeded(ctx.getStartMs())) {
                    throw new HarnessTimeoutException(policy.getTimeoutSeconds());
                }
                if (ctx.budgetExceeded()) {
                    log.warn("Agent 运行超出 token 预算，提前收尾: runId={}, spent={}",
                            ctx.getRunId(), ctx.getSpentTokens());
                    ctx.setFinishReason("budget_exceeded");
                    return finish(ctx, RunStatus.COMPLETED, listener);
                }

                int round = ctx.nextRound();
                listener.onRoundStart(round);
                int promptSize = ctx.messages().size();
                String promptIncrement = digest(ctx.messages(), promptCursor, promptSize);
                promptCursor = promptSize;

                long llmStart = System.currentTimeMillis();
                AssistantTurn turn;
                try {
                    turn = modelGateway.exchange(request, ctx.messages(), sink);
                } catch (Exception e) {
                    recordLlm(ctx, "failed", promptIncrement, null, null, llmStart, describe(e));
                    throw e instanceof HarnessException he ? he : new HarnessException(describe(e), e);
                }
                ctx.addAssistant(turn);
                lastTurn = turn;
                listener.onAssistantTurn(turn);
                recordLlm(ctx, "success", promptIncrement, turn, request.getModelName(), llmStart, null);

                if (!turn.hasToolCalls() || !toolsEnabled) {
                    ctx.setFinalText(turn.getContent());
                    if (ctx.getFinishReason() == null) {
                        ctx.setFinishReason("stop");
                    }
                    return finish(ctx, RunStatus.COMPLETED, listener);
                }

                ctx.setPendingToolCalls(turn.getToolCalls());
                HarnessOutcome suspended = executePendingTools(ctx, policy, listener);
                if (suspended != null) {
                    return suspended;
                }
                if (policy.roundExhausted(ctx.getRound())) {
                    log.warn("Agent 轮次耗尽仍未得到最终回答: runId={}, maxRounds={}",
                            ctx.getRunId(), policy.getMaxRounds());
                    ctx.setFinalText(lastTurn.getContent());
                    ctx.setFinishReason("max_rounds");
                    return finish(ctx, RunStatus.COMPLETED, listener);
                }
            }
        } catch (HarnessTimeoutException e) {
            ctx.setErrorMessage(e.getMessage());
            preservePartial(ctx, e);
            recordError(ctx, e);
            log.warn("Agent 运行超时: runId={}, {}", ctx.getRunId(), e.getMessage());
            return finish(ctx, RunStatus.FAILED, listener);
        } catch (Exception e) {
            if (ctx.isCancelled()) {
                log.info("Agent 运行被取消: runId={}", ctx.getRunId());
                preservePartial(ctx, e);
                return finish(ctx, RunStatus.CANCELLED, listener);
            }
            ctx.setErrorMessage(describe(e));
            preservePartial(ctx, e);
            recordError(ctx, e);
            log.error("Agent 运行失败: runId={}", ctx.getRunId(), e);
            return finish(ctx, RunStatus.FAILED, listener);
        }
    }

    /**
     * 执行本轮未跑完的工具调用。
     *
     * @return 需要挂起时返回终态结果，全部执行完返回 null
     */
    private HarnessOutcome executePendingTools(HarnessContext ctx, LoopPolicy policy, HarnessListener listener) {
        List<ChatResponse.ToolCall> calls = ctx.getPendingToolCalls();
        while (ctx.getPendingIndex() < calls.size()) {
            ChatResponse.ToolCall call = calls.get(ctx.getPendingIndex());
            ToolInvocation invocation = toInvocation(ctx, call);
            long startMs = System.currentTimeMillis();
            listener.onToolStart(invocation);
            ToolOutcome outcome = toolGateway.invoke(invocation, ctx);
            long endMs = System.currentTimeMillis();
            listener.onToolFinish(invocation, outcome);
            recordTool(ctx, invocation, outcome, startMs, endMs);

            if (outcome.getStatus() == ToolStatus.WAITING_APPROVAL) {
                return suspend(ctx, invocation, outcome, policy, listener);
            }
            if (outcome.getStatus() == ToolStatus.WAITING_LOCAL) {
                return suspendLocal(ctx, invocation, listener);
            }
            ctx.addToolResult(invocation, outcome);
            ctx.advancePendingIndex();
        }
        ctx.setPendingToolCalls(List.of());
        return null;
    }

    /**
     * 写检查点 + 建审批单，以挂起态结束本次请求（不占用线程等待人工）
     */
    private HarnessOutcome suspend(HarnessContext ctx, ToolInvocation invocation, ToolOutcome outcome,
                                   LoopPolicy policy, HarnessListener listener) {
        if (approvalStore == null) {
            ctx.addToolResult(invocation, ToolOutcome.denied("审批能力未启用"));
            ctx.advancePendingIndex();
            return null;
        }
        String resumeToken = recorder.checkpoint(ctx, RunStatus.WAITING_APPROVAL);
        long timeoutSeconds = approvalTimeout(ctx.getRequest(), policy);
        ApprovalRequest approval = approvalStore.create(ApprovalRequest.builder()
                .runId(ctx.getRunId())
                .sessionId(ctx.getSessionId())
                .applicationId(ctx.getApplicationId())
                .toolName(invocation.getCallName())
                .toolType(invocation.getToolType())
                .arguments(invocation.getArgumentsJson())
                .reason(outcome.getApprovalReason() == null ? outcome.getError() : outcome.getApprovalReason())
                .riskLevel(outcome.getRiskLevel())
                .resumeToken(resumeToken)
                .expireAt(System.currentTimeMillis() + timeoutSeconds * 1000L)
                .build());
        ctx.setPendingApproval(approval);
        HarnessOutcome result = HarnessOutcome.of(ctx, RunStatus.WAITING_APPROVAL);
        result.setPendingApproval(approval);
        listener.onWaitingApproval(approval);
        log.info("Agent 运行挂起等待审批: runId={}, tool={}, approvalId={}",
                ctx.getRunId(), invocation.getCallName(), approval.getApprovalId());
        return result;
    }

    /**
     * 本地工具挂起：写检查点，把待执行调用与一次性令牌交给调用方环境（如浏览器）执行，
     * 不创建审批单、不占用线程。结果由 {@link #completeLocalTool} 回传后续跑。
     */
    private HarnessOutcome suspendLocal(HarnessContext ctx, ToolInvocation invocation,
                                        HarnessListener listener) {
        String resumeToken = recorder.checkpoint(ctx, RunStatus.WAITING_LOCAL);
        LocalToolCall call = LocalToolCall.builder()
                .runId(ctx.getRunId())
                .sessionId(ctx.getSessionId())
                .applicationId(ctx.getApplicationId())
                .toolName(invocation.getCallName())
                .callId(invocation.getCallId())
                .arguments(invocation.getArgumentsJson())
                .resumeToken(resumeToken)
                .build();
        ctx.setPendingLocalTool(call);
        HarnessOutcome result = HarnessOutcome.of(ctx, RunStatus.WAITING_LOCAL);
        result.setPendingLocalTool(call);
        listener.onWaitingLocalTool(call);
        log.info("Agent 运行挂起等待本地工具执行: runId={}, tool={}, callId={}",
                ctx.getRunId(), invocation.getCallName(), invocation.getCallId());
        return result;
    }

    private void denyPendingCalls(HarnessContext ctx, String remark) {
        List<ChatResponse.ToolCall> calls = ctx.getPendingToolCalls();
        String reason = remark == null || remark.isBlank() ? "用户拒绝了该操作" : remark;
        for (int i = ctx.getPendingIndex(); i < calls.size(); i++) {
            ChatResponse.ToolCall call = calls.get(i);
            ctx.addToolResult(ToolInvocation.builder()
                            .runId(ctx.getRunId()).callId(call.getId()).callName(call.getName())
                            .round(ctx.getRound()).stepNo(ctx.nextStepNo()).build(),
                    ToolOutcome.denied(reason));
        }
        ctx.setPendingToolCalls(List.of());
    }

    // ==================== 留痕 ====================

    private HarnessOutcome finish(HarnessContext ctx, RunStatus status, HarnessListener listener) {
        HarnessOutcome outcome = HarnessOutcome.of(ctx, status);
        recorder.finishRun(ctx.getRunId(), outcome);
        listener.onComplete(outcome);
        return outcome;
    }

    /**
     * 失败/超时前保留已流式产出的部分正文：
     * 优先取异常携带的部分内容，其次取最后一轮助手输出，供业务侧落库与前端展示。
     */
    private void preservePartial(HarnessContext ctx, Exception e) {
        if (ctx.getFinalText() != null) {
            return;
        }
        String partial = null;
        if (e instanceof HarnessException he) {
            partial = he.getPartialContent();
        }
        if (partial != null && !partial.isBlank()) {
            ctx.setFinalText(partial);
        }
    }

    private void recordContext(HarnessRequest request, String runId, HarnessListener listener) {
        List<ContextFragment> fragments = request.getContextFragments();
        if (fragments == null || fragments.isEmpty()) {
            return;
        }
        listener.onContextReady(fragments);
        recorder.recordContext(runId, fragments);
    }

    private void recordLlm(HarnessContext ctx, String status, String input, AssistantTurn turn,
                           String modelName, long startMs, String error) {
        recorder.recordStep(AgentRunRecorder.StepRecord.builder()
                .runId(ctx.getRunId())
                .stepNo(ctx.nextStepNo())
                .round(ctx.getRound())
                .type("llm")
                .name(modelName == null ? "model" : modelName)
                .status(status)
                .input(input)
                .output(turn == null ? null : turn.getContent())
                .errorMessage(error)
                .inputTokens(turn == null ? null : turn.getInputTokens())
                .outputTokens(turn == null ? null : turn.getOutputTokens())
                .durationMs(System.currentTimeMillis() - startMs)
                .startMs(startMs)
                .endMs(System.currentTimeMillis())
                .build());
    }

    private void recordTool(HarnessContext ctx, ToolInvocation invocation, ToolOutcome outcome,
                            long startMs, long endMs) {
        recorder.recordStep(AgentRunRecorder.StepRecord.builder()
                .runId(ctx.getRunId())
                .stepNo(invocation.getStepNo())
                .round(invocation.getRound())
                .type("tool")
                .name(invocation.getCallName())
                .status(statusOf(outcome.getStatus()))
                .input(invocation.getArgumentsJson())
                .output(outcome.getOutput())
                .errorMessage(outcome.getError() != null ? outcome.getError() : outcome.getApprovalReason())
                .durationMs(endMs - startMs)
                .startMs(startMs)
                .endMs(endMs)
                .truncated(outcome.isTruncated())
                .build());
    }

    private void recordError(HarnessContext ctx, Exception e) {
        recorder.recordStep(AgentRunRecorder.StepRecord.builder()
                .runId(ctx.getRunId())
                .stepNo(ctx.nextStepNo())
                .round(ctx.getRound())
                .type("error")
                .name("harness")
                .status("failed")
                .errorMessage(describe(e))
                .startMs(System.currentTimeMillis())
                .endMs(System.currentTimeMillis())
                .build());
    }

    private String statusOf(ToolStatus status) {
        return status == null ? "failed" : status.value();
    }

    // ==================== 辅助 ====================

    private ToolInvocation toInvocation(HarnessContext ctx, ChatResponse.ToolCall call) {
        return ToolInvocation.builder()
                .runId(ctx.getRunId())
                .callId(call.getId())
                .callName(call.getName())
                .argumentsJson(call.getArguments())
                .arguments(parseArguments(call))
                .round(ctx.getRound())
                .stepNo(ctx.nextStepNo())
                .build();
    }

    private Map<String, Object> parseArguments(ChatResponse.ToolCall call) {
        try {
            Map<String, Object> parsed = com.alibaba.fastjson.JSON.parseObject(call.getArguments());
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("工具参数解析失败，按空参数处理: name={}, arguments={}, {}",
                    call.getName(), call.getArguments(), e.getMessage());
            return Map.of();
        }
    }

    private long approvalTimeout(HarnessRequest request, LoopPolicy policy) {
        HarnessConfig config = request.getConfig();
        if (config != null && config.getApproval() != null && config.getApproval().getTimeoutSeconds() != null) {
            return config.getApproval().getTimeoutSeconds();
        }
        return DEFAULT_APPROVAL_TIMEOUT_SECONDS;
    }

    /**
     * 本轮相对上轮的增量提示词（避免多轮上下文在 step 表里指数级放大）
     */
    private String digest(List<ChatMessage> messages, int from, int to) {
        if (from >= to) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            ChatMessage m = messages.get(i);
            sb.append(m.getRole()).append(": ")
                    .append(m.getContent() == null ? "" : m.getContent())
                    .append('\n');
        }
        return sb.toString();
    }

    private String describe(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }
}
