package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatResponse} 及其内部类 {@link ChatResponse.ToolCall} 单元测试。
 */
class ChatResponseTest {

    @Nested
    class ResponseContract {

        @Test
        void emptyBuilderShouldDefaultRoleToAssistant() {
            ChatResponse response = ChatResponse.builder().build();

            assertThat(response.getRole()).isEqualTo("assistant");
            assertThat(response.getContent()).isNull();
            assertThat(response.getPromptTokens()).isZero();
            assertThat(response.getCompletionTokens()).isZero();
            assertThat(response.getTotalTokens()).isZero();
            assertThat(response.getModel()).isNull();
            assertThat(response.getFinishReason()).isNull();
            assertThat(response.getToolCalls()).isNull();
        }

        @Test
        void noArgsConstructorShouldAlsoDefaultRoleToAssistant() {
            assertThat(new ChatResponse().getRole()).isEqualTo("assistant");
        }

        @Test
        void builderShouldAllowOverridingDefaultRole() {
            assertThat(ChatResponse.builder().role("tool").build().getRole()).isEqualTo("tool");
            assertThat(ChatResponse.builder().role(null).build().getRole()).isNull();
        }

        @Test
        void allArgsConstructorShouldFollowFieldOrder() {
            ChatResponse response = new ChatResponse("hi", "assistant", 1, 2, 3, "gpt", "stop", null);

            assertThat(response.getContent()).isEqualTo("hi");
            assertThat(response.getRole()).isEqualTo("assistant");
            assertThat(response.getPromptTokens()).isEqualTo(1);
            assertThat(response.getCompletionTokens()).isEqualTo(2);
            assertThat(response.getTotalTokens()).isEqualTo(3);
            assertThat(response.getModel()).isEqualTo("gpt");
            assertThat(response.getFinishReason()).isEqualTo("stop");
            assertThat(response.getToolCalls()).isNull();
        }

        @Test
        void settersShouldUpdateEveryField() {
            ChatResponse response = new ChatResponse();
            response.setContent("body");
            response.setRole("user");
            response.setPromptTokens(10);
            response.setCompletionTokens(20);
            response.setTotalTokens(30);
            response.setModel("qwen");
            response.setFinishReason("length");
            response.setToolCalls(List.of());

            assertThat(response.getContent()).isEqualTo("body");
            assertThat(response.getRole()).isEqualTo("user");
            assertThat(response.getPromptTokens()).isEqualTo(10);
            assertThat(response.getCompletionTokens()).isEqualTo(20);
            assertThat(response.getTotalTokens()).isEqualTo(30);
            assertThat(response.getModel()).isEqualTo("qwen");
            assertThat(response.getFinishReason()).isEqualTo("length");
            assertThat(response.getToolCalls()).isEmpty();
        }

        @Test
        void equalsShouldTakeToolCallsIntoAccount() {
            ChatResponse a = ChatResponse.builder().content("c").toolCalls(List.of(
                    ChatResponse.ToolCall.builder().id("1").build())).build();
            ChatResponse b = ChatResponse.builder().content("c").toolCalls(List.of(
                    ChatResponse.ToolCall.builder().id("1").build())).build();
            ChatResponse c = ChatResponse.builder().content("c").toolCalls(List.of(
                    ChatResponse.ToolCall.builder().id("2").build())).build();

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotEqualTo(ChatResponse.builder().content("c").build());
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        void toStringShouldContainCoreFields() {
            String text = ChatResponse.builder().content("answer").model("gpt-4o")
                    .finishReason("stop").totalTokens(7).build().toString();

            assertThat(text).contains("content=answer").contains("model=gpt-4o")
                    .contains("finishReason=stop").contains("totalTokens=7").contains("role=assistant");
        }

        @Test
        void tokenCountersShouldSupportZeroAndNegativeBoundaries() {
            ChatResponse response = ChatResponse.builder().promptTokens(0).completionTokens(-5).totalTokens(0).build();

            assertThat(response.getPromptTokens()).isZero();
            assertThat(response.getCompletionTokens()).isEqualTo(-5);
        }
    }

    @Nested
    class ToolCallContract {

        @Test
        void builderShouldPopulateAllFields() {
            ChatResponse.ToolCall call = ChatResponse.ToolCall.builder()
                    .id("call-1").name("get_weather").arguments("{\"city\":\"hz\"}").build();

            assertThat(call.getId()).isEqualTo("call-1");
            assertThat(call.getName()).isEqualTo("get_weather");
            assertThat(call.getArguments()).isEqualTo("{\"city\":\"hz\"}");
        }

        @Test
        void emptyBuilderAndNoArgsConstructorShouldGiveNulls() {
            assertThat(ChatResponse.ToolCall.builder().build().getId()).isNull();
            ChatResponse.ToolCall empty = new ChatResponse.ToolCall();
            assertThat(empty.getId()).isNull();
            assertThat(empty.getName()).isNull();
            assertThat(empty.getArguments()).isNull();
        }

        @Test
        void allArgsConstructorShouldFollowFieldOrder() {
            ChatResponse.ToolCall call = new ChatResponse.ToolCall("id", "fn", "{}");

            assertThat(call.getId()).isEqualTo("id");
            assertThat(call.getName()).isEqualTo("fn");
            assertThat(call.getArguments()).isEqualTo("{}");
        }

        @Test
        void settersShouldUpdateGetters() {
            ChatResponse.ToolCall call = new ChatResponse.ToolCall();
            call.setId("x");
            call.setName("n");
            call.setArguments("a");

            assertThat(call).isEqualTo(new ChatResponse.ToolCall("x", "n", "a"));
        }

        @Test
        void equalsHashCodeAndToStringShouldBeValueBased() {
            ChatResponse.ToolCall a = new ChatResponse.ToolCall("1", "f", "{}");
            ChatResponse.ToolCall b = new ChatResponse.ToolCall("1", "f", "{}");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(new ChatResponse.ToolCall("1", "f", "{\"a\":1}"));
            assertThat(a).isNotEqualTo(null);
            assertThat(a.toString()).contains("id=1").contains("name=f").contains("arguments={}");
        }

        @Test
        void toolCallShouldNotEqualOtherTypeEvenWithSameFields() {
            Object other = new Object() {
                @Override
                public boolean equals(Object o) {
                    return o instanceof ChatResponse.ToolCall;
                }
            };

            assertThat(new ChatResponse.ToolCall("1", "f", "{}")).isNotEqualTo(other);
            assertThat(other).isEqualTo(new ChatResponse.ToolCall("1", "f", "{}"));
        }
    }
}
