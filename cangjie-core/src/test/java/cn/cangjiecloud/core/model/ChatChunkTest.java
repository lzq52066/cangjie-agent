package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatChunk} 单元测试。
 */
class ChatChunkTest {

    @Nested
    class BuilderContract {

        @Test
        void builderShouldPopulateAllFields() {
            ChatResponse.ToolCall call = ChatResponse.ToolCall.builder()
                    .id("call-1").name("search").arguments("{\"q\":\"x\"}").build();

            ChatChunk chunk = ChatChunk.builder()
                    .delta("hel")
                    .done(true)
                    .finishReason("stop")
                    .error(null)
                    .inputTokens(11L)
                    .outputTokens(22L)
                    .totalTokens(33L)
                    .toolCalls(List.of(call))
                    .build();

            assertThat(chunk.getDelta()).isEqualTo("hel");
            assertThat(chunk.isDone()).isTrue();
            assertThat(chunk.getFinishReason()).isEqualTo("stop");
            assertThat(chunk.getError()).isNull();
            assertThat(chunk.getInputTokens()).isEqualTo(11L);
            assertThat(chunk.getOutputTokens()).isEqualTo(22L);
            assertThat(chunk.getTotalTokens()).isEqualTo(33L);
            assertThat(chunk.getToolCalls()).containsExactly(call);
        }

        @Test
        void builderShouldDefaultPrimitiveBooleanToFalseAndObjectsToNull() {
            ChatChunk chunk = ChatChunk.builder().delta("x").build();

            assertThat(chunk.isDone()).isFalse();
            assertThat(chunk.getFinishReason()).isNull();
            assertThat(chunk.getError()).isNull();
            assertThat(chunk.getInputTokens()).isNull();
            assertThat(chunk.getOutputTokens()).isNull();
            assertThat(chunk.getTotalTokens()).isNull();
            assertThat(chunk.getToolCalls()).isNull();
        }

        @Test
        void allArgsConstructorShouldFollowFieldOrder() {
            ChatChunk chunk = new ChatChunk("d", false, "length", "boom", 1L, 2L, 3L, List.of());

            assertThat(chunk.getDelta()).isEqualTo("d");
            assertThat(chunk.isDone()).isFalse();
            assertThat(chunk.getFinishReason()).isEqualTo("length");
            assertThat(chunk.getError()).isEqualTo("boom");
            assertThat(chunk.getInputTokens()).isEqualTo(1L);
            assertThat(chunk.getOutputTokens()).isEqualTo(2L);
            assertThat(chunk.getTotalTokens()).isEqualTo(3L);
            assertThat(chunk.getToolCalls()).isEmpty();
        }
    }

    @Nested
    class GetterSetterAndEquality {

        @Test
        void noArgsConstructorShouldGiveZeroValues() {
            ChatChunk chunk = new ChatChunk();

            assertThat(chunk.getDelta()).isNull();
            assertThat(chunk.isDone()).isFalse();
            assertThat(chunk.getToolCalls()).isNull();
        }

        @Test
        void settersShouldUpdateGetters() {
            ChatChunk chunk = new ChatChunk();
            chunk.setDelta("tail");
            chunk.setDone(true);
            chunk.setFinishReason("tool_calls");
            chunk.setError("timeout");
            chunk.setInputTokens(4L);
            chunk.setOutputTokens(5L);
            chunk.setTotalTokens(9L);
            chunk.setToolCalls(List.of(ChatResponse.ToolCall.builder().id("i").build()));

            assertThat(chunk.getDelta()).isEqualTo("tail");
            assertThat(chunk.isDone()).isTrue();
            assertThat(chunk.getFinishReason()).isEqualTo("tool_calls");
            assertThat(chunk.getError()).isEqualTo("timeout");
            assertThat(chunk.getTotalTokens()).isEqualTo(9L);
            assertThat(chunk.getToolCalls()).singleElement()
                    .satisfies(t -> assertThat(t.getId()).isEqualTo("i"));
        }

        @Test
        void equalsShouldConsiderEveryFieldIncludingBooleanAndTokenCounts() {
            ChatChunk base = ChatChunk.builder().delta("a").done(false).inputTokens(1L).build();
            ChatChunk same = ChatChunk.builder().delta("a").done(false).inputTokens(1L).build();

            assertThat(base).isEqualTo(same).hasSameHashCodeAs(same);
            assertThat(base).isNotEqualTo(ChatChunk.builder().delta("a").done(true).inputTokens(1L).build());
            assertThat(base).isNotEqualTo(ChatChunk.builder().delta("b").done(false).inputTokens(1L).build());
            assertThat(base).isNotEqualTo(ChatChunk.builder().delta("a").done(false).inputTokens(2L).build());
            assertThat(base).isNotEqualTo(null);
            assertThat(base).isNotEqualTo("string");
        }

        @Test
        void toStringShouldExposeFlaggedErrorState() {
            String text = ChatChunk.builder().delta("x").done(true).error("rate limited").build().toString();

            assertThat(text).contains("delta=x").contains("done=true").contains("error=rate limited");
        }

        @Test
        void tokenFieldsShouldAcceptZeroAndNegativeBoundaries() {
            ChatChunk chunk = ChatChunk.builder().inputTokens(0L).outputTokens(-1L).totalTokens(Long.MAX_VALUE).build();

            assertThat(chunk.getInputTokens()).isZero();
            assertThat(chunk.getOutputTokens()).isEqualTo(-1L);
            assertThat(chunk.getTotalTokens()).isEqualTo(Long.MAX_VALUE);
        }
    }
}
