package cn.cangjiecloud.core.harness;

/**
 * Harness 执行异常（统一包装，终态由 run 记录为 failed）
 */
public class HarnessException extends RuntimeException {

    /** 流式中断前已成功产出的部分回答（可能为空），用于失败时落库保留 */
    private final String partialContent;

    public HarnessException(String message) {
        this(message, null, null);
    }

    public HarnessException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public HarnessException(String message, String partialContent, Throwable cause) {
        super(message, cause);
        this.partialContent = partialContent;
    }

    public String getPartialContent() {
        return partialContent;
    }
}
