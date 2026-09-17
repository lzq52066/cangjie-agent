package cn.cangjiecloud.core.harness;

/**
 * Harness 执行异常（统一包装，终态由 run 记录为 failed）
 */
public class HarnessException extends RuntimeException {

    public HarnessException(String message) {
        super(message);
    }

    public HarnessException(String message, Throwable cause) {
        super(message, cause);
    }
}
