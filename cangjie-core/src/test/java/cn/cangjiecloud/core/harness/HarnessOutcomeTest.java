package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessOutcome} 单元测试。
 */
class HarnessOutcomeTest {

    @Test
    void isSuccessShouldBeTrueOnlyWhenCompleted() {
        HarnessOutcome completed = HarnessOutcome.builder().status(RunStatus.COMPLETED).build();
        HarnessOutcome failed = HarnessOutcome.builder().status(RunStatus.FAILED).build();

        assertThat(completed.isSuccess()).isTrue();
        assertThat(failed.isSuccess()).isFalse();
    }

    @Test
    void ofShouldMapFieldsFromContext() {
        HarnessRequest request = HarnessRequest.builder()
                .runId("run-1")
                .contextMessages(List.of(ChatMessage.user("seed")))
                .build();
        HarnessContext ctx = new HarnessContext(request);
        ctx.nextRound();
        ctx.setFinalText("the answer");
        ctx.setFinishReason("stop");

        HarnessOutcome outcome = HarnessOutcome.of(ctx, RunStatus.COMPLETED);

        assertThat(outcome.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(outcome.getRunId()).isEqualTo("run-1");
        assertThat(outcome.getFinalText()).isEqualTo("the answer");
        assertThat(outcome.getRounds()).isEqualTo(1);
        assertThat(outcome.getConversation()).hasSize(1);
    }

    @Test
    void ofShouldSnapshotConversationDefensively() {
        HarnessContext ctx = new HarnessContext(HarnessRequest.builder().runId("r").build());
        HarnessOutcome outcome = HarnessOutcome.of(ctx, RunStatus.COMPLETED);

        ctx.addAssistant(AssistantTurn.builder().content("new").build());

        assertThat(outcome.getConversation()).isEmpty();
    }
}
