package cn.cangjiecloud.agent;

import cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型熔断器单元测试
 */
class ModelCircuitBreakerTest {

    private ModelCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new ModelCircuitBreaker();
        ReflectionTestUtils.setField(breaker, "enabled", true);
        ReflectionTestUtils.setField(breaker, "failureThreshold", 3);
        ReflectionTestUtils.setField(breaker, "cooldownSeconds", 1L);
    }

    @Test
    void opensAfterConsecutiveFailures() {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("m1");
        }
        assertFalse(breaker.allowRequest("m1"), "连续失败达到阈值应熔断");
        assertTrue(breaker.allowRequest("m2"), "其他模型不受影响");
    }

    @Test
    void successResetsFailureCount() {
        breaker.recordFailure("m1");
        breaker.recordFailure("m1");
        breaker.recordSuccess("m1");
        breaker.recordFailure("m1");
        breaker.recordFailure("m1");
        assertTrue(breaker.allowRequest("m1"), "成功调用后失败计数应清零，不再熔断");
    }

    @Test
    void halfOpenAfterCooldown() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("m1");
        }
        assertFalse(breaker.allowRequest("m1"), "冷却期内应拒绝");
        Thread.sleep(1100);
        assertTrue(breaker.allowRequest("m1"), "冷却结束应半开放行探测");
    }

    @Test
    void failureDuringHalfOpenReopens() {
        // 以 0 秒冷却熔断 → 立即进入半开
        ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("m1");
        }
        assertTrue(breaker.allowRequest("m1"), "冷却 0 秒应立即半开");
        // 半开探测失败（失败数累计超过阈值）→ 以 60 秒冷却重新熔断
        ReflectionTestUtils.setField(breaker, "cooldownSeconds", 60L);
        breaker.recordFailure("m1");
        assertFalse(breaker.allowRequest("m1"), "半开失败后应重新熔断");
    }

    @Test
    void halfOpenAllowsOnlyOneProbe() {
        // 以 0 秒冷却熔断 → 熔断后立即可半开探测
        ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure("m1");
        }
        assertTrue(breaker.allowRequest("m1"), "首个探测请求应放行");
        assertFalse(breaker.allowRequest("m1"), "半开态其余并发请求应拒绝");
        breaker.recordSuccess("m1");
        assertTrue(breaker.allowRequest("m1"), "探测成功恢复闭合后应放行");
    }

    @Test
    void disabledBreakerAlwaysAllows() {
        ReflectionTestUtils.setField(breaker, "enabled", false);
        for (int i = 0; i < 10; i++) {
            breaker.recordFailure("m1");
        }
        assertTrue(breaker.allowRequest("m1"), "熔断关闭时应始终放行");
    }
}
