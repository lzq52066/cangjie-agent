package cn.cangjiecloud.core.harness;

/**
 * Agent 执行（run）状态
 */
public enum RunStatus {

    /** 执行中 */
    RUNNING("running"),

    /** 正常完成 */
    COMPLETED("completed"),

    /** 执行失败（模型异常、超时、工具异常等） */
    FAILED("failed"),

    /** 客户端断开或被主动取消 */
    CANCELLED("cancelled"),

    /** 等待人工审批（已持久化检查点，可通过 resume 恢复） */
    WAITING_APPROVAL("waiting_approval"),

    /** 等待调用方环境执行本地工具（已持久化检查点，结果回传后通过 resume 恢复） */
    WAITING_LOCAL("waiting_local");

    private final String value;

    RunStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RunStatus of(String value) {
        for (RunStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        return FAILED;
    }
}
