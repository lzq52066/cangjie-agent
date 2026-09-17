package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审计钩子（order 900，最后执行）：只对"未执行成功"的调用告警。
 * <p>
 * 成功/失败的完整明细由引擎通过 {@code AgentRunRecorder} 落库，这里只补一条便于按日志排查
 * 治理拦截的告警——策略拒绝、审批挂起都属于人工配置产生的偏差，需要显性暴露。
 */
@Slf4j
@Component
public class AuditHook implements ToolCallHook {

    @Override
    public Decision before(ToolInvocation invocation, HarnessContext context) {
        return Decision.proceed();
    }

    @Override
    public void after(ToolInvocation invocation, ToolOutcome outcome, HarnessContext context) {
        if (outcome == null) {
            return;
        }
        ToolStatus status = outcome.getStatus();
        if (status == ToolStatus.DENIED) {
            log.warn("工具调用被策略拒绝: runId={}, tool={}, 原因={}",
                    invocation.getRunId(), invocation.getCallName(), outcome.getError());
        } else if (status == ToolStatus.WAITING_APPROVAL) {
            log.info("工具调用挂起等待审批: runId={}, tool={}, risk={}",
                    invocation.getRunId(), invocation.getCallName(), outcome.getRiskLevel());
        } else if (status == ToolStatus.TIMEOUT) {
            log.warn("工具调用超时: runId={}, tool={}, 耗时={}ms",
                    invocation.getRunId(), invocation.getCallName(), outcome.getDurationMs());
        } else if (outcome.isTruncated()) {
            log.info("工具输出已截断回喂: runId={}, tool={}, 长度={}",
                    invocation.getRunId(), invocation.getCallName(),
                    outcome.getOutput() == null ? 0 : outcome.getOutput().length());
        }
    }

    @Override
    public int getOrder() {
        return 900;
    }
}
