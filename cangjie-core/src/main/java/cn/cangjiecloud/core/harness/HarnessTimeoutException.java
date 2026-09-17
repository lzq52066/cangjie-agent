package cn.cangjiecloud.core.harness;

/**
 * Agent 循环总超时
 */
public class HarnessTimeoutException extends HarnessException {

    private static final String MESSAGE_PREFIX = "对话处理超时：";

    private final long timeoutSeconds;

    public HarnessTimeoutException(long timeoutSeconds) {
        super(MESSAGE_PREFIX + "模型与工具调用总时长超过 " + timeoutSeconds + " 秒，已终止");
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 判断错误信息是否来自总超时，供业务侧复刻改造前的收尾分支（超时时不额外包装错误）
     */
    public static boolean isTimeoutMessage(String message) {
        return message != null && message.startsWith(MESSAGE_PREFIX);
    }
}
