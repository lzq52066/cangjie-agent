package cn.cangjiecloud.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批决策后恢复执行的结果。
 * <p>
 * 恢复执行以同步方式产出（不再复用挂起时的 SSE 连接），因此这里既承载最终回答，
 * 也承载"后续工具再次触发审批"的新审批单——调用方需要据此继续下一轮决策。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResumeDTO {

    /** 被恢复的 run */
    private String runId;

    /** 所属会话 */
    private String sessionId;

    /** 执行状态：completed / waiting_approval / failed / cancelled */
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

    /** 新审批单（status=waiting_approval 时非空） */
    private PendingApproval pendingApproval;

    /**
     * 恢复执行过程中新产生的待审批项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingApproval {

        private String approvalId;

        private String runId;

        /** 待执行的工具函数名 */
        private String toolName;

        private String toolType;

        /** 待执行参数（JSON 字符串） */
        private String arguments;

        /** 触发审批的原因 */
        private String reason;

        /** 风险等级：low / medium / high */
        private String riskLevel;

        /** 过期时间戳（毫秒） */
        private long expireAt;

        /** 下一次恢复用的一次性令牌 */
        private String resumeToken;
    }
}
