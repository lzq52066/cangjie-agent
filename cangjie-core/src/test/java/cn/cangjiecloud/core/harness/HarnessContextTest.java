package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessContext} 单元测试：计数、消息追加、审批/取消语义与快照恢复往返。
 */
class HarnessContextTest {

    private static HarnessRequest baseRequest() {
        return HarnessRequest.builder()
                .runId("run-1")
                .sessionId("sess-1")
                .applicationId("app-1")
                .traceId("trace-1")
                .contextMessages(List.of(ChatMessage.user("seed")))
                .build();
    }

    @Test
    void constructorShouldSeedIdentityAndContextMessages() {
        HarnessContext ctx = new HarnessContext(baseRequest());

        assertThat(ctx.getRunId()).isEqualTo("run-1");
        assertThat(ctx.getSessionId()).isEqualTo("sess-1");
        assertThat(ctx.getApplicationId()).isEqualTo("app-1");
        assertThat(ctx.getTraceId()).isEqualTo("trace-1");
        assertThat(ctx.messages()).hasSize(1);
    }

    @Test
    void countersShouldIncrementRoundAndStep() {
        HarnessContext ctx = new HarnessContext(baseRequest());

        assertThat(ctx.nextRound()).isEqualTo(1);
        assertThat(ctx.nextRound()).isEqualTo(2);
        assertThat(ctx.nextStepNo()).isEqualTo(1);
        assertThat(ctx.getRound()).isEqualTo(2);
    }

    @Nested
    class AssistantAndTool {

        @Test
        void addAssistantShouldAppendMessageAndAccumulateTokens() {
            HarnessContext ctx = new HarnessContext(baseRequest());
            AssistantTurn turn = AssistantTurn.builder()
                    .content("thinking")
                    .inputTokens(10).outputTokens(5).totalTokens(15)
                    .finishReason("stop")
                    .build();

            ctx.addAssistant(turn);

            assertThat(ctx.messages()).hasSize(2);
            assertThat(ctx.messages().get(1).getRole()).isEqualTo("assistant");
            assertThat(ctx.getInputTokens()).isEqualTo(10);
            assertThat(ctx.getOutputTokens()).isEqualTo(5);
            assertThat(ctx.getTotalTokens()).isEqualTo(15);
            assertThat(ctx.getSpentTokens()).isEqualTo(15);
            assertThat(ctx.getFinishReason()).isEqualTo("stop");
        }

        @Test
        void addAssistantWithToolCallsShouldPersistToolCallRefs() {
            HarnessContext ctx = new HarnessContext(baseRequest());
            AssistantTurn turn = AssistantTurn.builder()
                    .content("")
                    .toolCalls(List.of(ChatResponse.ToolCall.builder()
                            .id("call-9").name("search").arguments("{\"q\":\"a\"}").build()))
                    .build();

            ctx.addAssistant(turn);

            ChatMessage assistant = ctx.messages().get(1);
            assertThat(assistant.getToolCalls()).hasSize(1);
            assertThat(assistant.getToolCalls().get(0).getId()).isEqualTo("call-9");
        }

        @Test
        void addToolResultShouldAppendToolMessageAndCount() {
            HarnessContext ctx = new HarnessContext(baseRequest());
            ToolInvocation invocation = ToolInvocation.builder()
                    .callId("call-1").callName("search").build();

            ctx.addToolResult(invocation, ToolOutcome.success("result", 1L));

            ChatMessage tool = ctx.messages().get(1);
            assertThat(tool.getRole()).isEqualTo("tool");
            assertThat(tool.getToolCallId()).isEqualTo("call-1");
            assertThat(ctx.getToolCallCount()).isEqualTo(1);
        }
    }

    @Nested
    class PendingCalls {

        @Test
        void setPendingToolCallsShouldResetIndexAndNullToEmpty() {
            HarnessContext ctx = new HarnessContext(baseRequest());
            ctx.setPendingToolCalls(List.of(
                    ChatResponse.ToolCall.builder().id("a").build(),
                    ChatResponse.ToolCall.builder().id("b").build()));

            assertThat(ctx.getPendingIndex()).isZero();
            assertThat(ctx.currentPendingCall().getId()).isEqualTo("a");

            ctx.advancePendingIndex();
            assertThat(ctx.currentPendingCall().getId()).isEqualTo("b");

            ctx.setPendingToolCalls(null);
            assertThat(ctx.getPendingToolCalls()).isEmpty();
            assertThat(ctx.currentPendingCall()).isNull();
        }
    }

    @Nested
    class Approval {

        @Test
        void approveAndIsApprovedShouldTrackCallIds() {
            HarnessContext ctx = new HarnessContext(baseRequest());

            assertThat(ctx.isApproved("call-1")).isFalse();
            ctx.approve("call-1");
            assertThat(ctx.isApproved("call-1")).isTrue();
        }

        @Test
        void approveNullShouldBeIgnored() {
            HarnessContext ctx = new HarnessContext(baseRequest());

            ctx.approve(null);
            assertThat(ctx.isApproved(null)).isFalse();
        }
    }

    @Nested
    class CancellationAndBudget {

        @Test
        void isCancelledShouldReflectCancelFlag() {
            HarnessRequest request = baseRequest();
            HarnessContext ctx = new HarnessContext(request);

            assertThat(ctx.isCancelled()).isFalse();
            request.getCancelFlag().set(true);
            assertThat(ctx.isCancelled()).isTrue();
        }

        @Test
        void isCancelledShouldBeFalseWhenFlagMissing() {
            HarnessRequest request = HarnessRequest.builder().cancelFlag(null).build();

            assertThat(new HarnessContext(request).isCancelled()).isFalse();
        }

        @Test
        void budgetExceededShouldBeFalseWithoutConfig() {
            HarnessContext ctx = new HarnessContext(baseRequest());

            assertThat(ctx.budgetExceeded()).isFalse();
        }

        @Test
        void budgetExceededShouldCompareSpentAgainstBudget() {
            HarnessRequest request = HarnessRequest.builder()
                    .runId("r")
                    .config(HarnessConfig.builder().runTokenBudget(100L).build())
                    .build();
            HarnessContext ctx = new HarnessContext(request);

            assertThat(ctx.budgetExceeded()).isFalse();

            AssistantTurn turn = AssistantTurn.builder().totalTokens(100).build();
            ctx.addAssistant(turn);
            assertThat(ctx.budgetExceeded()).isTrue();
        }
    }

    @Nested
    class SnapshotRestore {

        @Test
        void restoreShouldRebuildStateFromSnapshot() {
            HarnessContext original = new HarnessContext(baseRequest());
            original.nextRound();
            original.addAssistant(AssistantTurn.builder()
                    .content("reply").inputTokens(10).outputTokens(5).totalTokens(15).build());
            original.setPendingToolCalls(List.of(
                    ChatResponse.ToolCall.builder().id("pending-1").name("fn").build()));
            original.advancePendingIndex();

            ResumeState snapshot = original.snapshot();

            HarnessContext restored = HarnessContext.restore(baseRequest(), snapshot);

            assertThat(restored.getRunId()).isEqualTo("run-1");
            assertThat(restored.getRound()).isEqualTo(original.getRound());
            assertThat(restored.messages()).hasSameSizeAs(original.messages());
            assertThat(restored.getInputTokens()).isEqualTo(10);
            assertThat(restored.getOutputTokens()).isEqualTo(5);
            assertThat(restored.getTotalTokens()).isEqualTo(15);
            assertThat(restored.getSpentTokens()).isEqualTo(15);
            assertThat(restored.getPendingToolCalls()).hasSize(1);
            assertThat(restored.getPendingIndex()).isEqualTo(1);
        }

        @Test
        void snapshotShouldBeIndependentOfLaterMutation() {
            HarnessContext original = new HarnessContext(baseRequest());
            ResumeState snapshot = original.snapshot();

            original.addAssistant(AssistantTurn.builder().content("later").build());

            assertThat(snapshot.getMessages()).hasSize(1);
        }
    }
}
