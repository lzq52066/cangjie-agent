package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次工具调用的结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutcome {

    private ToolStatus status;

    /** 工具输出（回填给模型的正文） */
    private String output;

    /** 错误信息（失败/拒绝时） */
    private String error;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 输出是否被截断 */
    private boolean truncated;

    /** 风险等级（status=WAITING_APPROVAL 时使用） */
    private String riskLevel;

    /** 挂起原因（status=WAITING_APPROVAL 时展示给审批人） */
    private String approvalReason;

    /**
     * 回喂模型的原文（工具实际执行过才有值）。
     * <p>
     * 存在时优先于按状态拼装的内容，保证与改造前逐字节一致——失败结果由工具侧自行给出
     * {@code {"success":false,...}} 结构，网关不重新包装，避免丢失工具自带的错误字段。
     */
    private String modelContent;

    public boolean isSuccess() {
        return status == ToolStatus.SUCCESS;
    }

    public static ToolOutcome success(String output, long durationMs) {
        return ToolOutcome.builder().status(ToolStatus.SUCCESS).output(output).durationMs(durationMs).build();
    }

    public static ToolOutcome failed(String error, long durationMs) {
        return ToolOutcome.builder().status(ToolStatus.FAILED).error(error).durationMs(durationMs).build();
    }

    public static ToolOutcome denied(String reason) {
        return ToolOutcome.builder().status(ToolStatus.DENIED).error(reason).build();
    }

    public static ToolOutcome timeout(String message, long durationMs) {
        return ToolOutcome.builder().status(ToolStatus.TIMEOUT).error(message).durationMs(durationMs).build();
    }

    public static ToolOutcome waitingApproval(String reason, String riskLevel) {
        return ToolOutcome.builder().status(ToolStatus.WAITING_APPROVAL)
                .approvalReason(reason).riskLevel(riskLevel == null ? "medium" : riskLevel).build();
    }

    /**
     * 回填给模型的 tool 消息正文（沿用平台既有格式，保证模型行为一致性）
     */
    public String toModelContent(ToolInvocation inv) {
        if (modelContent != null) {
            return modelContent;
        }
        if (isSuccess()) {
            return "工具调用结果(" + inv.getCallName() + "): " + (output == null ? "" : output);
        }
        String reason = error != null ? error : "工具执行失败";
        return "工具调用结果(" + inv.getCallName() + "): " + "{\"success\":false,\"error\":\"" + reason + "\"}";
    }
}
