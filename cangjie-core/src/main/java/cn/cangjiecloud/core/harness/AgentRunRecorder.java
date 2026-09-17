package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent run / step 留痕记录器（SPI，由 cangjie-observability 实现）。
 * <p>
 * 所有写入必须内部异步且不可抛出影响主流程的异常——留痕失败只能降级，绝不能打断对话。
 */
public interface AgentRunRecorder {

    /**
     * 开启一次 run，返回 runId（同时把 TraceContext 关联到该 run）
     */
    String startRun(HarnessRequest request);

    /**
     * 记录上下文装配结果（各 slot 实际占用，用于上下文预算回归）
     */
    void recordContext(String runId, List<ContextFragment> fragments);

    /**
     * 记录一个步骤（llm / tool / hook / error）
     */
    void recordStep(StepRecord record);

    /**
     * 持久化检查点并把 run 置为指定状态（如 waiting_approval），返回一次性 resumeToken
     */
    String checkpoint(HarnessContext context, RunStatus status);

    /**
     * 结束 run（终态 + 汇总统计）
     */
    void finishRun(String runId, HarnessOutcome outcome);

    /**
     * 读取挂起中的 run 检查点（不存在或已非挂起态返回 null）
     *
     * @param runId       run ID
     * @param resumeToken 一次性恢复令牌，校验通过后立即失效
     */
    PausedRun loadPaused(String runId, String resumeToken);

    /**
     * 挂起中的 run 快照
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PausedRun {
        private String runId;
        private String status;
        private ResumeState state;
        /** 关联的审批单 ID（非审批挂起时为空） */
        private String approvalId;

        // ==== 重装配执行输入所需的身份字段（检查点只存会话状态，配置态需按这些重新解析） ====
        private String traceId;
        private String applicationId;
        private String applicationName;
        private String sessionId;
        private String userId;
        private String modelId;
        private String modelName;
        /** chat / workflow / subagent / debug */
        private String harnessType;
        private String parentRunId;
        private int depth;
    }

    /**
     * 单步留痕参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StepRecord {
        private String runId;
        private int stepNo;
        private int round;
        /** llm / tool / context / hook / error */
        private String type;
        /** 模型名或函数名 */
        private String name;
        private String status;
        /** 入参（llm 只记录本轮增量，避免上下文指数放大） */
        private String input;
        private String output;
        private String errorMessage;
        private Long inputTokens;
        private Long outputTokens;
        private long durationMs;
        private long startMs;
        private long endMs;
        private boolean truncated;
    }
}
