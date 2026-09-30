package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolStatus} 单元测试。
 */
class ToolStatusTest {

    @Test
    void valueShouldReturnWireFormOfEachStatus() {
        assertThat(ToolStatus.SUCCESS.value()).isEqualTo("success");
        assertThat(ToolStatus.FAILED.value()).isEqualTo("failed");
        assertThat(ToolStatus.DENIED.value()).isEqualTo("denied");
        assertThat(ToolStatus.TIMEOUT.value()).isEqualTo("timeout");
        assertThat(ToolStatus.WAITING_APPROVAL.value()).isEqualTo("waiting_approval");
        assertThat(ToolStatus.WAITING_LOCAL.value()).isEqualTo("waiting_local");
    }

    @Test
    void valuesShouldContainAllSixStatuses() {
        assertThat(ToolStatus.values()).hasSize(6);
    }
}
