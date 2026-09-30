package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessTimeoutException} 单元测试。
 */
class HarnessTimeoutExceptionTest {

    @Test
    void constructorShouldCarryTimeoutSecondsAndMessage() {
        HarnessTimeoutException e = new HarnessTimeoutException(42);

        assertThat(e.getTimeoutSeconds()).isEqualTo(42);
        assertThat(e.getMessage()).contains("42");
        assertThat(e).isInstanceOf(HarnessException.class);
    }

    @Test
    void isTimeoutMessageShouldRecognizePrefixedMessage() {
        String message = new HarnessTimeoutException(10).getMessage();

        assertThat(HarnessTimeoutException.isTimeoutMessage(message)).isTrue();
    }

    @Test
    void isTimeoutMessageShouldRejectNullOrUnrelatedMessage() {
        assertThat(HarnessTimeoutException.isTimeoutMessage(null)).isFalse();
        assertThat(HarnessTimeoutException.isTimeoutMessage("some other error")).isFalse();
    }
}
