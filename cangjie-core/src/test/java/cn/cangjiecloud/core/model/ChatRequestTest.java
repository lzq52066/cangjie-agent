package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatRequest} 单元测试，重点覆盖 {@code @Builder.Default} 默认值回退分支。
 */
class ChatRequestTest {

    @Nested
    class DefaultValues {

        @Test
        void emptyBuilderShouldApplyDefaults() {
            ChatRequest request = ChatRequest.builder().build();

            assertThat(request.getTemperature()).isEqualTo(0.7);
            assertThat(request.getTopP()).isEqualTo(1.0);
            assertThat(request.isStream()).isFalse();
            // 非 @Builder.Default 字段保持类型零值
            assertThat(request.getMaxTokens()).isZero();
            assertThat(request.getModel()).isNull();
            assertThat(request.getMessages()).isNull();
            assertThat(request.getExtra()).isNull();
            assertThat(request.getTools()).isNull();
            assertThat(request.getToolChoice()).isNull();
        }

        @Test
        void noArgsConstructorShouldApplyDefaultsToo() {
            ChatRequest request = new ChatRequest();

            assertThat(request.getTemperature()).isEqualTo(0.7);
            assertThat(request.getTopP()).isEqualTo(1.0);
            assertThat(request.isStream()).isFalse();
        }

        @Test
        void builderShouldOverrideEveryDefault() {
            ChatRequest request = ChatRequest.builder()
                    .temperature(0.0)
                    .topP(0.5)
                    .stream(true)
                    .build();

            assertThat(request.getTemperature()).isZero();
            assertThat(request.getTopP()).isEqualTo(0.5);
            assertThat(request.isStream()).isTrue();
        }

        @Test
        void explicitFalseStreamShouldNotBeTreatedAsMissing() {
            assertThat(ChatRequest.builder().stream(false).build().isStream()).isFalse();
            assertThat(ChatRequest.builder().stream(true).build().isStream()).isTrue();
        }
    }

    @Nested
    class FullConstruction {

        @Test
        void builderShouldPopulateAllFields() {
            List<ChatMessage> messages = List.of(ChatMessage.user("hi"));
            Map<String, Object> extra = new HashMap<>();
            extra.put("seed", 1);
            List<Map<String, Object>> tools = List.of(Map.of("type", "function"));

            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(messages)
                    .temperature(1.9)
                    .maxTokens(2048)
                    .topP(0.9)
                    .stream(true)
                    .extra(extra)
                    .tools(tools)
                    .toolChoice("auto")
                    .build();

            assertThat(request.getModel()).isEqualTo("gpt-4o");
            assertThat(request.getMessages()).isEqualTo(messages);
            assertThat(request.getTemperature()).isEqualTo(1.9);
            assertThat(request.getMaxTokens()).isEqualTo(2048);
            assertThat(request.getTopP()).isEqualTo(0.9);
            assertThat(request.isStream()).isTrue();
            assertThat(request.getExtra()).containsEntry("seed", 1);
            assertThat(request.getTools()).isEqualTo(tools);
            assertThat(request.getToolChoice()).isEqualTo("auto");
        }

        @Test
        void allArgsConstructorShouldFollowFieldOrder() {
            ChatRequest request = new ChatRequest("m", List.of(), 0.2, 10, 0.3, true, null, null, "none", null, null);

            assertThat(request.getModel()).isEqualTo("m");
            assertThat(request.getMessages()).isEmpty();
            assertThat(request.getTemperature()).isEqualTo(0.2);
            assertThat(request.getMaxTokens()).isEqualTo(10);
            assertThat(request.getTopP()).isEqualTo(0.3);
            assertThat(request.isStream()).isTrue();
            assertThat(request.getToolChoice()).isEqualTo("none");
        }
    }

    @Nested
    class GettersSettersAndEquality {

        @Test
        void settersShouldUpdateEveryField() {
            ChatRequest request = new ChatRequest();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("k", "v");
            request.setModel("qwen-max");
            request.setMessages(List.of(ChatMessage.system("s")));
            request.setTemperature(2.0);
            request.setMaxTokens(-1);
            request.setTopP(0.0);
            request.setStream(true);
            request.setExtra(extra);
            request.setTools(List.of(Map.of("type", "function")));
            request.setToolChoice("required");

            assertThat(request.getModel()).isEqualTo("qwen-max");
            assertThat(request.getMessages()).hasSize(1);
            assertThat(request.getTemperature()).isEqualTo(2.0);
            assertThat(request.getMaxTokens()).isEqualTo(-1);
            assertThat(request.getTopP()).isZero();
            assertThat(request.isStream()).isTrue();
            assertThat(request.getExtra()).isEqualTo(extra);
            assertThat(request.getTools()).hasSize(1);
            assertThat(request.getToolChoice()).isEqualTo("required");
        }

        @Test
        void equalsShouldBeValueBasedAcrossEveryField() {
            ChatRequest a = ChatRequest.builder().model("m").temperature(0.7).maxTokens(5).build();
            ChatRequest b = ChatRequest.builder().model("m").temperature(0.7).maxTokens(5).build();

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(ChatRequest.builder().model("m").temperature(0.7).maxTokens(6).build());
            assertThat(a).isNotEqualTo(ChatRequest.builder().model("m2").temperature(0.7).maxTokens(5).build());
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo(new Object());
        }

        @Test
        void toStringShouldContainModelAndPagingParams() {
            String text = ChatRequest.builder().model("deepseek").maxTokens(128).toolChoice("auto").build().toString();

            assertThat(text).contains("model=deepseek").contains("maxTokens=128").contains("toolChoice=auto")
                    .contains("stream=false");
        }

        @Test
        void emptyCollectionsShouldDifferFromNullCollectionsInEquals() {
            ChatRequest empty = ChatRequest.builder().messages(List.of()).build();
            ChatRequest nullMessages = ChatRequest.builder().build();

            assertThat(empty).isNotEqualTo(nullMessages);
            assertThat(empty.getMessages()).isEmpty();
        }
    }
}
