package cn.cangjiecloud.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ModelProviderFactory} 单元测试。
 */
class ModelProviderFactoryTest {

    private ModelProvider provider(ModelType type) {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.getType()).thenReturn(type);
        return provider;
    }

    @Test
    void constructorShouldRegisterProvidersByType() {
        ModelProvider openai = provider(ModelType.OPENAI);
        ModelProvider qwen = provider(ModelType.QWEN);

        ModelProviderFactory factory = new ModelProviderFactory(List.of(openai, qwen));

        assertThat(factory.get(ModelType.OPENAI)).isSameAs(openai);
        assertThat(factory.get(ModelType.QWEN)).isSameAs(qwen);
    }

    @Test
    void getShouldThrowWhenTypeNotRegistered() {
        ModelProviderFactory factory = new ModelProviderFactory(List.of(provider(ModelType.OPENAI)));

        assertThatThrownBy(() -> factory.get(ModelType.DEEPSEEK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到模型提供者")
                .hasMessageContaining("DEEPSEEK");
    }

    @Test
    void emptyFactoryShouldRejectEveryType() {
        ModelProviderFactory factory = new ModelProviderFactory(List.of());

        assertThat(factory.listTypes()).isEmpty();
        for (ModelType type : ModelType.values()) {
            assertThatThrownBy(() -> factory.get(type)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void nullTypeLookupShouldHitConcurrentHashMapNullKeyGuard() {
        ModelProviderFactory factory = new ModelProviderFactory(List.of(provider(ModelType.OPENAI)));

        assertThatThrownBy(() -> factory.get((ModelType) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void lastRegisteredProviderWinsForDuplicateType() {
        ModelProvider first = provider(ModelType.OPENAI);
        ModelProvider second = provider(ModelType.OPENAI);

        ModelProviderFactory factory = new ModelProviderFactory(List.of(first, second));

        assertThat(factory.get(ModelType.OPENAI)).isSameAs(second);
    }

    @Test
    void getCodeShouldResolveThroughModelTypeOf() {
        ModelProvider openai = provider(ModelType.OPENAI);
        ModelProvider custom = provider(ModelType.CUSTOM);
        ModelProviderFactory factory = new ModelProviderFactory(List.of(openai, custom));

        assertThat(factory.get("openai")).isSameAs(openai);
        assertThat(factory.get("OPENAI")).isSameAs(openai);
        // null 兜底为 OPENAI
        assertThat(factory.get((String) null)).isSameAs(openai);
        // 未知编码兜底为 CUSTOM
        assertThat(factory.get("no-such-model")).isSameAs(custom);
    }

    @Test
    void getCodeShouldThrowWhenResolvedTypeMissing() {
        ModelProviderFactory factory = new ModelProviderFactory(List.of(provider(ModelType.OPENAI)));

        assertThatThrownBy(() -> factory.get("qwen")).isInstanceOf(IllegalArgumentException.class);
        // 未知编码 -> CUSTOM -> 未注册
        assertThatThrownBy(() -> factory.get("whatever")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listTypesShouldReturnSnapshotOfRegisteredTypes() {
        ModelProviderFactory factory = new ModelProviderFactory(
                List.of(provider(ModelType.OPENAI), provider(ModelType.OLLAMA)));

        assertThat(factory.listTypes()).containsExactlyInAnyOrder(ModelType.OPENAI, ModelType.OLLAMA);
        // List.copyOf 快照不可变
        assertThatThrownBy(() -> factory.listTypes().add(ModelType.QWEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullProviderListShouldFailFastInConstructor() {
        assertThatThrownBy(() -> new ModelProviderFactory(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void retrievedProviderShouldStillDelegateChat() {
        ModelProvider openai = provider(ModelType.OPENAI);
        ChatResponse response = ChatResponse.builder().content("pong").build();
        when(openai.chat(ChatRequest.builder().model("gpt").build())).thenReturn(response);

        ModelProviderFactory factory = new ModelProviderFactory(List.of(openai));

        assertThat(factory.get(ModelType.OPENAI).chat(ChatRequest.builder().model("gpt").build())).isSameAs(response);
    }
}
