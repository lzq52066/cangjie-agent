package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RunStatus} 单元测试。
 */
class RunStatusTest {

    @Test
    void valueShouldReturnWireFormOfEachStatus() {
        assertThat(RunStatus.RUNNING.value()).isEqualTo("running");
        assertThat(RunStatus.COMPLETED.value()).isEqualTo("completed");
        assertThat(RunStatus.FAILED.value()).isEqualTo("failed");
        assertThat(RunStatus.CANCELLED.value()).isEqualTo("cancelled");
        assertThat(RunStatus.WAITING_APPROVAL.value()).isEqualTo("waiting_approval");
        assertThat(RunStatus.WAITING_LOCAL.value()).isEqualTo("waiting_local");
    }

    @Test
    void ofShouldParseByValueIgnoringCase() {
        assertThat(RunStatus.of("running")).isEqualTo(RunStatus.RUNNING);
        assertThat(RunStatus.of("COMPLETED")).isEqualTo(RunStatus.COMPLETED);
        assertThat(RunStatus.of("Waiting_Approval")).isEqualTo(RunStatus.WAITING_APPROVAL);
    }

    @Test
    void ofShouldFallbackToFailedWhenValueUnknown() {
        assertThat(RunStatus.of("not-a-status")).isEqualTo(RunStatus.FAILED);
        assertThat(RunStatus.of("")).isEqualTo(RunStatus.FAILED);
        assertThat(RunStatus.of(null)).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void valuesShouldContainAllSixStatuses() {
        assertThat(RunStatus.values()).hasSize(6);
    }
}
