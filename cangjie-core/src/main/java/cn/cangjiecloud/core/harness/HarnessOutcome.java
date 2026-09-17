package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次 Agent 执行的结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessOutcome {

    private RunStatus status;

    private String runId;

    /** 最终回答文本 */
    private String finalText;

    private String finishReason;

    private int rounds;

    private int toolCallCount;

    private long inputTokens;

    private long outputTokens;

    private long totalTokens;

    private long durationMs;

    private String errorMessage;

    /** status=WAITING_APPROVAL 时非空 */
    private ApprovalRequest pendingApproval;

    /**
     * 执行结束时的完整会话消息（含系统提示、工具回填）。
     * <p>
     * 供业务侧按既有语义记录 llm_trace 的 promptContent，避免引擎内外各维护一份会话副本。
     */
    private List<ChatMessage> conversation;

    public boolean isSuccess() {
        return status == RunStatus.COMPLETED;
    }

    public static HarnessOutcome of(HarnessContext ctx, RunStatus status) {
        return HarnessOutcome.builder()
                .status(status)
                .runId(ctx.getRunId())
                .finalText(ctx.getFinalText())
                .finishReason(ctx.getFinishReason())
                .rounds(ctx.getRound())
                .toolCallCount(ctx.getToolCallCount())
                .inputTokens(ctx.getInputTokens())
                .outputTokens(ctx.getOutputTokens())
                .totalTokens(ctx.getTotalTokens())
                .durationMs(System.currentTimeMillis() - ctx.getStartMs())
                .errorMessage(ctx.getErrorMessage())
                .pendingApproval(ctx.getPendingApproval())
                .conversation(List.copyOf(ctx.messages()))
                .build();
    }
}
