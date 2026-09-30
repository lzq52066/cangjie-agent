package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolOutcome} 单元测试，覆盖静态工厂与 {@link ToolOutcome#toModelContent(ToolInvocation)}。
 */
class ToolOutcomeTest {

    private static ToolInvocation invocation(String name) {
        return ToolInvocation.builder().callId("c1").callName(name).build();
    }

    @Nested
    class Factories {

        @Test
        void successShouldBuildSuccessfulOutcome() {
            ToolOutcome outcome = ToolOutcome.success("data", 12L);

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.SUCCESS);
            assertThat(outcome.getOutput()).isEqualTo("data");
            assertThat(outcome.getDurationMs()).isEqualTo(12L);
            assertThat(outcome.isSuccess()).isTrue();
        }

        @Test
        void failedShouldBuildFailedOutcome() {
            ToolOutcome outcome = ToolOutcome.failed("boom", 5L);

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.FAILED);
            assertThat(outcome.getError()).isEqualTo("boom");
            assertThat(outcome.isSuccess()).isFalse();
        }

        @Test
        void deniedShouldBuildDeniedOutcomeWithReason() {
            ToolOutcome outcome = ToolOutcome.denied("not allowed");

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.DENIED);
            assertThat(outcome.getError()).isEqualTo("not allowed");
        }

        @Test
        void timeoutShouldBuildTimeoutOutcome() {
            ToolOutcome outcome = ToolOutcome.timeout("slow", 30_000L);

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.TIMEOUT);
            assertThat(outcome.getError()).isEqualTo("slow");
            assertThat(outcome.getDurationMs()).isEqualTo(30_000L);
        }

        @Test
        void waitingApprovalShouldDefaultRiskToMediumWhenNull() {
            ToolOutcome outcome = ToolOutcome.waitingApproval("dangerous op", null);

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.WAITING_APPROVAL);
            assertThat(outcome.getApprovalReason()).isEqualTo("dangerous op");
            assertThat(outcome.getRiskLevel()).isEqualTo("medium");
        }

        @Test
        void waitingApprovalShouldKeepGivenRiskLevel() {
            ToolOutcome outcome = ToolOutcome.waitingApproval("dangerous op", "high");

            assertThat(outcome.getRiskLevel()).isEqualTo("high");
        }

        @Test
        void waitingLocalShouldBuildWaitingLocalOutcome() {
            ToolOutcome outcome = ToolOutcome.waitingLocal();

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.WAITING_LOCAL);
        }
    }

    @Nested
    class ModelContent {

        @Test
        void shouldRenderSuccessWithOutput() {
            ToolOutcome outcome = ToolOutcome.success("42", 1L);

            assertThat(outcome.toModelContent(invocation("calc")))
                    .isEqualTo("工具调用结果(calc): 42");
        }

        @Test
        void shouldRenderEmptyStringWhenSuccessOutputIsNull() {
            ToolOutcome outcome = ToolOutcome.success(null, 1L);

            assertThat(outcome.toModelContent(invocation("calc")))
                    .isEqualTo("工具调用结果(calc): ");
        }

        @Test
        void shouldRenderFailureJsonWithError() {
            ToolOutcome outcome = ToolOutcome.failed("disk full", 1L);

            assertThat(outcome.toModelContent(invocation("io")))
                    .isEqualTo("工具调用结果(io): {\"success\":false,\"error\":\"disk full\"}");
        }

        @Test
        void shouldRenderGenericFailureWhenErrorMissing() {
            ToolOutcome outcome = ToolOutcome.builder().status(ToolStatus.FAILED).build();

            assertThat(outcome.toModelContent(invocation("io")))
                    .isEqualTo("工具调用结果(io): {\"success\":false,\"error\":\"工具执行失败\"}");
        }

        @Test
        void explicitModelContentShouldTakePrecedenceOverStatusFormat() {
            ToolOutcome outcome = ToolOutcome.builder()
                    .status(ToolStatus.FAILED)
                    .modelContent("{\"success\":false,\"code\":500}")
                    .build();

            assertThat(outcome.toModelContent(invocation("io")))
                    .isEqualTo("{\"success\":false,\"code\":500}");
        }
    }
}
