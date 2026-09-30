package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessException} 单元测试。
 */
class HarnessExceptionTest {

    @Test
    void messageConstructorShouldLeavePartialContentNull() {
        HarnessException e = new HarnessException("bad thing");

        assertThat(e.getMessage()).isEqualTo("bad thing");
        assertThat(e.getPartialContent()).isNull();
        assertThat(e.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructorShouldKeepCause() {
        Throwable cause = new IllegalStateException("root");
        HarnessException e = new HarnessException("wrapped", cause);

        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getPartialContent()).isNull();
    }

    @Test
    void fullConstructorShouldKeepPartialContent() {
        HarnessException e = new HarnessException("failed mid stream", "partial answer", null);

        assertThat(e.getPartialContent()).isEqualTo("partial answer");
    }
}
