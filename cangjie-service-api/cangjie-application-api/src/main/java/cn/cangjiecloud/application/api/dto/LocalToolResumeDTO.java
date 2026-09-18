package cn.cangjiecloud.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地工具结果回传后恢复执行的结果。
 * <p>
 * 与 {@link ApprovalResumeDTO} 同构：既承载最终回答，也承载恢复过程中再次遇到的
 * 本地工具调用（前端需继续在浏览器侧执行并回传）或待审批单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalToolResumeDTO {

    /** 被恢复的 run */
    private String runId;

    /** 所属会话 */
    private String sessionId;

    /** 执行状态：completed / waiting_local / waiting_approval / failed / cancelled */
    private String status;

    /** 最终回答（status=completed 时非空，已作为 assistant 消息落库） */
    private String message;

    /** 失败原因（status=failed 时非空） */
    private String errorMessage;

    /** 结束原因：stop / max_rounds / budget_exceeded / denied */
    private String finishReason;

    /** 本次恢复后累计的推理轮次 */
    private Integer rounds;

    /** 本次恢复后累计的工具调用数 */
    private Integer toolCallCount;

    /** 消耗 token 总数 */
    private Integer tokens;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 本次恢复执行耗时（毫秒） */
    private Long duration;

    /** 新的本地工具调用（status=waiting_local 时非空） */
    private PendingLocalTool pendingLocalTool;

    /** 恢复过程中命中审批（status=waiting_approval 时非空） */
    private ApprovalResumeDTO.PendingApproval pendingApproval;

    /**
     * 恢复执行过程中新产生的待浏览器执行项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingLocalTool {

        private String runId;

        /** 待执行的 tool_call id */
        private String callId;

        /** 待执行的工具函数名 */
        private String toolName;

        /** 待执行参数（JSON 字符串） */
        private String arguments;

        /** 下一次恢复用的一次性令牌 */
        private String resumeToken;
    }
}
