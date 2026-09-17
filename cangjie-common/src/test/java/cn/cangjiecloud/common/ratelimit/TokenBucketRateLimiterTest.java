package cn.cangjiecloud.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌桶限流器 {@link TokenBucketRateLimiter} 单元测试。
 */
class TokenBucketRateLimiterTest {

    @Test
    void constructorShouldRejectNonPositiveParams() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, -2));
    }

    @Test
    void burstCapacityAllowsInitialBurstThenRejects() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("k"), "突发容量内应放行");
        }
        assertFalse(limiter.tryAcquire("k"), "突发耗尽后应限流");
    }

    @Test
    void tokensRefillOverTime() throws InterruptedException {
        // 20 qps，突发容量 1：约 50ms 补一个令牌
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(20, 1);
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        Thread.sleep(150);
        assertTrue(limiter.tryAcquire("k"), "令牌补充后应放行");
    }

    @Test
    void keysAreIsolated() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"), "不同限流 key 互不影响");
    }
}
