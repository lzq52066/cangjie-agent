package cn.cangjiecloud.common.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌桶限流器（单机版）
 * <p>
 * 按 key 懒创建令牌桶，线程安全。适用于单实例部署；
 * 多实例部署时可替换为 Redis 实现，对外接口保持不变。
 */
public class TokenBucketRateLimiter {

    private final double permitsPerSecond;
    private final double maxPermits;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double permitsPerSecond, double maxPermits) {
        if (permitsPerSecond <= 0 || maxPermits <= 0) {
            throw new IllegalArgumentException("限流参数必须为正数");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = maxPermits;
    }

    /**
     * 尝试获取一个令牌
     *
     * @param key 限流维度标识（如应用 ID）
     * @return true 放行，false 触发限流
     */
    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket(maxPermits))
                .tryAcquire(permitsPerSecond, maxPermits);
    }

    private static final class Bucket {
        private double permits;
        private long lastRefillNanos;

        Bucket(double maxPermits) {
            this.permits = maxPermits;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire(double permitsPerSecond, double maxPermits) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                permits = Math.min(maxPermits, permits + elapsedSeconds * permitsPerSecond);
                lastRefillNanos = now;
            }
            if (permits >= 1.0) {
                permits -= 1.0;
                return true;
            }
            return false;
        }
    }
}
