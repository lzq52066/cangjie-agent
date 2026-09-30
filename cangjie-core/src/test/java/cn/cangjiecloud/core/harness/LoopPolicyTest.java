package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoopPolicy} 单元测试。
 */
class LoopPolicyTest {

    // ---------- 默认值与构建 ----------

    @Test
    void builderShouldApplyDefaults() {
        LoopPolicy policy = LoopPolicy.builder().build();

        assertThat(policy.getMaxRounds()).isEqualTo(5);
        assertThat(policy.getTimeoutSeconds()).isEqualTo(300);
        assertThat(policy.isParallelTools()).isFalse();
        assertThat(policy.isFinalizeOnExhaust()).isFalse();
        assertThat(policy.getToolTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void ofShouldSetMaxRoundsAndTimeoutKeepingOtherDefaults() {
        LoopPolicy policy = LoopPolicy.of(8, 60);

        assertThat(policy.getMaxRounds()).isEqualTo(8);
        assertThat(policy.getTimeoutSeconds()).isEqualTo(60);
        assertThat(policy.isParallelTools()).isFalse();
    }

    @Test
    void tightShouldReturnSerialCompactPolicy() {
        LoopPolicy policy = LoopPolicy.tight(2, 15);

        assertThat(policy.getMaxRounds()).isEqualTo(2);
        assertThat(policy.getTimeoutSeconds()).isEqualTo(15);
        assertThat(policy.isParallelTools()).isFalse();
    }

    // ---------- deadlineExceeded ----------

    @Test
    void deadlineExceededShouldReturnFalseWhenWithinTimeout() {
        LoopPolicy policy = LoopPolicy.of(5, 300);
        long start = System.currentTimeMillis();

        assertThat(policy.deadlineExceeded(start)).isFalse();
    }

    @Test
    void deadlineExceededShouldReturnTrueWhenTimeoutElapsed() {
        LoopPolicy policy = LoopPolicy.of(5, 1);
        long start = System.currentTimeMillis() - 2_000L;

        assertThat(policy.deadlineExceeded(start)).isTrue();
    }

    @Test
    void deadlineExceededShouldNeverTriggerWhenTimeoutDisabled() {
        LoopPolicy policy = LoopPolicy.builder().timeoutSeconds(0).build();
        long ancientStart = 0L;

        assertThat(policy.deadlineExceeded(ancientStart)).isFalse();
    }

    // ---------- roundExhausted ----------

    @Test
    void roundExhaustedShouldReturnFalseBeforeLimit() {
        LoopPolicy policy = LoopPolicy.of(5, 300);

        assertThat(policy.roundExhausted(1)).isFalse();
        assertThat(policy.roundExhausted(4)).isFalse();
    }

    @Test
    void roundExhaustedShouldReturnTrueAtAndAfterLimit() {
        LoopPolicy policy = LoopPolicy.of(5, 300);

        assertThat(policy.roundExhausted(5)).isTrue();
        assertThat(policy.roundExhausted(6)).isTrue();
    }

    @Test
    void roundExhaustedShouldNeverTriggerWhenLimitDisabled() {
        LoopPolicy policy = LoopPolicy.builder().maxRounds(0).build();

        assertThat(policy.roundExhausted(100)).isFalse();
    }
}
