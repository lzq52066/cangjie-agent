package cn.cangjiecloud.core.workflow;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 指数退避重试执行器
 * <p>
 * 在工作流节点执行过程中提供节点级重试能力，避免单次节点失败导致整个工作流终止。
 */
@Slf4j
public final class RetryExecutor {

    private RetryExecutor() {
        // 工具类，禁止实例化
    }

    /**
     * 使用指数退避重试执行任务
     *
     * @param callable      待执行任务
     * @param maxRetries    最大重试次数（不含首次执行）
     * @param delayMs       首次重试延迟（毫秒）
     * @param backoffMultiplier 退避倍数（每次重试后延迟乘以该倍数）
     * @param retryable     可重试异常判定（为 null 则所有异常都重试）
     * @param nodeName      节点名称（日志用）
     * @return 任务执行结果
     */
    public static <T> T execute(Callable<T> callable, int maxRetries, long delayMs,
                                 double backoffMultiplier, Predicate<Exception> retryable,
                                 String nodeName) throws Exception {
        long currentDelay = delayMs;
        int attempt = 0;

        while (true) {
            try {
                return callable.call();
            } catch (Exception e) {
                attempt++;
                boolean canRetry = attempt <= maxRetries
                        && (retryable == null || retryable.test(e));
                if (!canRetry) {
                    log.error("节点 [{}] 执行失败，重试已耗尽 ({}/{})", nodeName, attempt - 1, maxRetries + 1, e);
                    throw e;
                }
                log.warn("节点 [{}] 执行异常，{}/{} 次重试，等待 {}ms: {}",
                        nodeName, attempt, maxRetries + 1, currentDelay, e.getMessage());
                Thread.sleep(currentDelay);
                currentDelay = (long) (currentDelay * backoffMultiplier);
            }
        }
    }

    /**
     * 简化版：所有异常都重试，默认退避倍数 2.0
     */
    public static <T> T execute(Callable<T> callable, int maxRetries, long delayMs, String nodeName)
            throws Exception {
        return execute(callable, maxRetries, delayMs, 2.0, null, nodeName);
    }
}