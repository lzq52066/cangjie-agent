package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelProvider} 单元测试。
 * <p>
 * 接口唯一带实现的成员是 default 方法 {@code embed(String)}（默认抛
 * {@link UnsupportedOperationException}），其余为抽象方法；测试用轻量替身 + Mockito 覆盖。
 */
class ModelProviderTest {

    /** 不实现 embed 的最小提供者，用于验证 default 行为。 */
    private static class MinimalProvider implements ModelProvider {

        private ChatRequest lastChatRequest;
        private ChatRequest lastStreamRequest;

        @Override
        public ChatResponse chat(ChatRequest request) {
            lastChatRequest = request;
            return ChatResponse.builder().content("pong").model("stub").build();
        }

        @Override
        public Stream<ChatChunk> streamChat(ChatRequest request) {
            lastStreamRequest = request;
            return Stream.of(
                    ChatChunk.builder().delta("po").build(),
                    ChatChunk.builder().delta("ng").build(),
                    ChatChunk.builder().done(true).finishReason("stop").build());
        }

        @Override
        public ModelType getType() {
            return ModelType.CUSTOM;
        }
    }

    /** 额外实现 embed 的提供者，用于验证 default 方法可被覆盖。 */
    private static final class EmbeddableProvider extends MinimalProvider {
        @Override
        public float[] embed(String text) {
            return new float[]{(float) text.length(), 1.0f};
        }
    }

    @Nested
    class DefaultEmbed {

        @Test
        void embedShouldThrowUnsupportedOperationExceptionByDefault() {
            ModelProvider provider = new MinimalProvider();

            assertThatThrownBy(() -> provider.embed("hello"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("该模型不支持嵌入");
        }

        @Test
        void embedShouldThrowForNullAndEmptyTextToo() {
            ModelProvider provider = new MinimalProvider();

            assertThatThrownBy(() -> provider.embed(null))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> provider.embed(""))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("该模型不支持嵌入");
        }

        @Test
        void embedShouldBeOverridableByImplementation() {
            ModelProvider provider = new EmbeddableProvider();

            assertThat(provider.embed("abcd")).containsExactly(4.0f, 1.0f);
            assertThat(provider.embed("")).containsExactly(0.0f, 1.0f);
        }

        @Test
        void embedShouldBeDeclaredAsTheOnlyDefaultMethod() {
            List<Method> defaults = Arrays.stream(ModelProvider.class.getDeclaredMethods())
                    .filter(Method::isDefault)
                    .toList();

            assertThat(ModelProvider.class.isInterface()).isTrue();
            assertThat(defaults).extracting(Method::getName).containsExactly("embed");
            assertThat(ModelProvider.class.getDeclaredMethods()).hasSize(4);
        }

        @Test
        void defaultMethodOnMockShouldStillThrowWhenRealMethodInvoked() {
            ModelProvider provider = mock(ModelProvider.class, invocation -> {
                if (invocation.getMethod().isDefault()) {
                    return invocation.callRealMethod();
                }
                return null;
            });

            assertThatThrownBy(() -> provider.embed("any"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("该模型不支持嵌入");
        }
    }

    @Nested
    class AbstractContract {

        @Test
        void chatShouldReturnProviderResponse() {
            MinimalProvider provider = new MinimalProvider();
            ChatRequest request = ChatRequest.builder().model("stub-model").build();

            ChatResponse response = provider.chat(request);

            assertThat(provider.lastChatRequest).isSameAs(request);
            assertThat(response.getContent()).isEqualTo("pong");
            assertThat(response.getRole()).isEqualTo("assistant");
        }

        @Test
        void streamChatShouldEmitDeltasAndTerminalChunk() {
            MinimalProvider provider = new MinimalProvider();
            ChatRequest request = ChatRequest.builder().model("stub-model").stream(true).build();

            List<ChatChunk> chunks = provider.streamChat(request).toList();

            assertThat(provider.lastStreamRequest).isSameAs(request);
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getDelta()).isEqualTo("po");
            assertThat(chunks.get(0).isDone()).isFalse();
            assertThat(chunks.get(2).isDone()).isTrue();
            assertThat(chunks.get(2).getFinishReason()).isEqualTo("stop");
        }

        @Test
        void getTypeShouldIdentifyProvider() {
            assertThat(new MinimalProvider().getType()).isSameAs(ModelType.CUSTOM);
            assertThat(new MinimalProvider().getType().getCode()).isEqualTo("custom");
        }
    }

    @Nested
    class MockitoStubs {

        @Test
        void mockShouldAllowStubbingEveryAbstractMethod() {
            ModelProvider provider = mock(ModelProvider.class);
            ChatRequest request = ChatRequest.builder().model("gpt-4o").build();
            when(provider.getType()).thenReturn(ModelType.OPENAI);
            when(provider.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("hi").build());
            when(provider.streamChat(any(ChatRequest.class)))
                    .thenReturn(Stream.of(ChatChunk.builder().done(true).build()));
            when(provider.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            assertThat(provider.getType()).isSameAs(ModelType.OPENAI);
            assertThat(provider.chat(request).getContent()).isEqualTo("hi");
            assertThat(provider.streamChat(request).count()).isEqualTo(1);
            assertThat(provider.embed("x")).containsExactly(0.1f, 0.2f);
            verify(provider).embed("x");
        }

        @Test
        void unstubbedEmbedOnPlainMockShouldReturnNullInsteadOfThrowing() {
            ModelProvider provider = mock(ModelProvider.class);

            // Mockito 会同时代理 default 方法，未打桩时返回类型默认值（数组为 null）
            assertThat(provider.embed("x")).isNull();
            assertThat(provider.getType()).isNull();
        }

        @Test
        void mockShouldBeUsableThroughFactoryStyleDispatch() {
            ModelProvider provider = mock(ModelProvider.class);
            when(provider.getType()).thenReturn(ModelType.QWEN);
            ModelProviderFactory factory = new ModelProviderFactory(List.of(provider));

            assertThat(factory.get(ModelType.QWEN)).isSameAs(provider);
            assertThat(factory.get("qwen")).isSameAs(provider);
        }
    }
}
