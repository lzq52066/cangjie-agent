package cn.cangjiecloud.model.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelCircuitBreaker} 单元测试。
 * <p>
 * 全程纯内存：通过 ReflectionTestUtils 注入失败阈值、冷却时长与启用开关，
 * 覆盖闭合 → 熔断（open）→ 半开探测（half-open）→ 恢复/重新熔断的完整状态流转，
 * 以及半开期 CAS 探测名额的互斥行为。
 */
class ModelCircuitBreakerTest {

    private ModelCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new ModelCircuitBreaker();
        ReflectionTestUtils.setField(breaker, "enabled", true);
        ReflectionTestUtils.setField(breaker, "failureThreshold", 3);
        ReflectionTestUtils.setField(breaker, "cooldownSeconds", 60L);
    }

    /** 连续记录失败直到触发熔断 */
    private void tripBreaker(String modelId) {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(modelId);
        }
    }

    @Nested
    class ClosedState {

        @Test
        void shouldAllowRequestWhenNoRecordExists() {
            // 从未记录过的模型处于隐式闭合态，直接放行
            assertThat(breaker.allowRequest("brand-new")).isTrue();
        }

        @Test
        void shouldRemainClosedBelowFailureThreshold() {
            breaker.recordFailure("m1");
            breaker.recordFailure("m1");

            // 未达阈值仍为闭合态，继续放行
            assertThat(breaker.allowRequest("m1")).isTrue();
        }

        @Test
        void shouldIsolateStatePerModel() {
            tripBreaker("m1");

            // 某个模型熔断不影响其它模型
            assertThat(breaker.allowRequest("m1")).isFalse();
            assertThat(breaker.allowRequest("m2")).isTrue();
        }

        @Test
        void shouldTreatNullModelIdAsAlwaysAllowed() {
            // null 不参与统计，直接放行；记录方法也应安全忽略
            assertThat(breaker.allowRequest(null)).isTrue();
            breaker.recordFailure(null);
            breaker.recordSuccess(null);
            assertThat(breaker.allowRequest(null)).isTrue();
        }
    }

    @Nested
    class OpenState {

        @Test
        void shouldOpenAfterReachingFailureThreshold() {
            tripBreaker("m1");

            assertThat(breaker.allowRequest("m1")).isFalse();
        }

        @Test
        void shouldNotReopenWhenFailuresContinueWhileAlreadyOpen() {
            tripBreaker("m1");

            // 熔断期间继续失败：openUntilMs 已被设置，不应被反复重置（否则冷却永远不结束）
            long before = System.currentTimeMillis();
            breaker.recordFailure("m1");
            breaker.recordFailure("m1");
            assertThat(breaker.allowRequest("m1")).isFalse();
            assertThat(System.currentTimeMillis()).isGreaterThanOrEqualTo(before);
        }

        @Test
        void shouldAllowRequestAgainWhenCooldownElapses() throws Exception {
            // 冷却 0 秒：熔断后下一次判定即进入半开窗口
            ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
            tripBreaker("m1");

            // 冷却结束，首个请求通过 CAS 抢占探测名额并放行
            assertThat(breaker.allowRequest("m1")).isTrue();
        }
    }

    @Nested
    class HalfOpenState {

        @Test
        void shouldAllowOnlyOneProbeRequestViaCas() {
            ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
            tripBreaker("m1");

            // 第一个请求抢到唯一探测名额
            assertThat(breaker.allowRequest("m1")).isTrue();
            // 探测进行中，其余并发请求一律拒绝，避免恢复期穿透
            assertThat(breaker.allowRequest("m1")).isFalse();
        }

        @Test
        void shouldCloseBreakerWhenProbeSucceeds() {
            ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
            tripBreaker("m1");
            assertThat(breaker.allowRequest("m1")).isTrue();

            // 探测成功：清零失败计数、恢复闭合
            breaker.recordSuccess("m1");
            assertThat(breaker.allowRequest("m1")).isTrue();
        }

        @Test
        void shouldReopenWhenProbeFails() {
            ReflectionTestUtils.setField(breaker, "cooldownSeconds", 0L);
            tripBreaker("m1");
            assertThat(breaker.allowRequest("m1")).isTrue();

            // 探测失败：失败数继续累计并超过阈值，重新熔断（此处冷却恢复为较长时间）
            ReflectionTestUtils.setField(breaker, "cooldownSeconds", 60L);
            breaker.recordFailure("m1");
            assertThat(breaker.allowRequest("m1")).isFalse();
        }
    }

    @Nested
    class SuccessRecording {

        @Test
        void shouldResetFailureCountAndKeepClosed() {
            breaker.recordFailure("m1");
            breaker.recordFailure("m1");
            breaker.recordSuccess("m1");

            // 成功后计数清零：再失败两次仍达不到阈值
            breaker.recordFailure("m1");
            breaker.recordFailure("m1");
            assertThat(breaker.allowRequest("m1")).isTrue();
        }

        @Test
        void shouldIgnoreSuccessForUnknownModel() {
            // 没有状态记录时调用成功不产生任何副作用
            breaker.recordSuccess("ghost");
            assertThat(breaker.allowRequest("ghost")).isTrue();
        }
    }

    @Nested
    class DisabledBreaker {

        @Test
        void shouldAlwaysAllowEvenAfterManyFailures() {
            ReflectionTestUtils.setField(breaker, "enabled", false);

            for (int i = 0; i < 20; i++) {
                breaker.recordFailure("m1");
            }

            assertThat(breaker.allowRequest("m1")).isTrue();
        }

        @Test
        void shouldIgnoreSuccessAndFailureRecordingWhenDisabled() {
            ReflectionTestUtils.setField(breaker, "enabled", false);
            breaker.recordFailure("m1");
            breaker.recordSuccess("m1");

            // 关闭状态下不创建任何模型状态
            assertThat(breaker.allowRequest("m1")).isTrue();
        }
    }
}
