package cn.cangjiecloud.core.harness;

/**
 * 单次工具调用的结果状态
 */
public enum ToolStatus {

    /** 执行成功 */
    SUCCESS("success"),

    /** 执行失败（下游异常、脚本抛错等） */
    FAILED("failed"),

    /** 被策略或人工拒绝（未执行） */
    DENIED("denied"),

    /** 执行超时 */
    TIMEOUT("timeout"),

    /** 需人工审批，未执行（run 应挂起等待 resume） */
    WAITING_APPROVAL("waiting_approval"),

    /** 需由调用方环境（如浏览器）执行的本地工具，服务端不执行（run 应挂起等待结果回传） */
    WAITING_LOCAL("waiting_local");

    private final String value;

    ToolStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
