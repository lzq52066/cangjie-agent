package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssistantTurn} 单元测试。
 */
class AssistantTurnTest {

    @Test
    void hasToolCallsShouldBeFalseWhenListNullOrEmpty() {
        assertThat(AssistantTurn.builder().build().hasToolCalls()).isFalse();
        assertThat(AssistantTurn.builder().toolCalls(List.of()).build().hasToolCalls()).isFalse();
    }

    @Test
    void hasToolCallsShouldBeTrueWhenNonEmpty() {
        AssistantTurn turn = AssistantTurn.builder()
                .toolCalls(List.of(ChatResponse.ToolCall.builder().id("1").build()))
                .build();

        assertThat(turn.hasToolCalls()).isTrue();
    }

    @Test
    void tokensOrZeroShouldPreferTotalTokensWhenPositive() {
        AssistantTurn turn = AssistantTurn.builder()
                .inputTokens(10).outputTokens(5).totalTokens(20).build();

        assertThat(turn.tokensOrZero()).isEqualTo(20);
    }

    @Test
    void tokensOrZeroShouldFallBackToSumWhenTotalNonPositive() {
        AssistantTurn turn = AssistantTurn.builder()
                .inputTokens(10).outputTokens(5).totalTokens(0).build();

        assertThat(turn.tokensOrZero()).isEqualTo(15);
    }
}
