package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TextSplitterFactory} 单元测试。
 */
class TextSplitterFactoryTest {

    private TextSplitter splitter(SplitStrategy strategy) {
        TextSplitter splitter = mock(TextSplitter.class);
        when(splitter.getStrategy()).thenReturn(strategy);
        return splitter;
    }

    @Test
    void constructorShouldRegisterEverySplitterByStrategy() {
        TextSplitter smart = splitter(SplitStrategy.SMART);
        TextSplitter custom = splitter(SplitStrategy.CUSTOM);

        TextSplitterFactory factory = new TextSplitterFactory(List.of(smart, custom));

        assertThat(factory.get(SplitStrategy.SMART)).isSameAs(smart);
        assertThat(factory.get(SplitStrategy.CUSTOM)).isSameAs(custom);
    }

    @Test
    void getShouldThrowWhenStrategyNotRegistered() {
        TextSplitter smart = splitter(SplitStrategy.SMART);
        TextSplitterFactory factory = new TextSplitterFactory(List.of(smart));

        assertThatThrownBy(() -> factory.get(SplitStrategy.CUSTOM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到切片策略实现")
                .hasMessageContaining("CUSTOM");
    }

    @Test
    void emptyRegistryShouldRejectAnyStrategy() {
        TextSplitterFactory factory = new TextSplitterFactory(List.of());

        assertThatThrownBy(() -> factory.get(SplitStrategy.SMART)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullStrategyLookupShouldHitConcurrentHashMapNullKeyGuard() {
        TextSplitterFactory factory = new TextSplitterFactory(List.of(splitter(SplitStrategy.SMART)));

        assertThatThrownBy(() -> factory.get((SplitStrategy) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void lastRegisteredSplitterWinsForDuplicateStrategy() {
        TextSplitter first = splitter(SplitStrategy.SMART);
        TextSplitter second = splitter(SplitStrategy.SMART);

        TextSplitterFactory factory = new TextSplitterFactory(List.of(first, second));

        assertThat(factory.get(SplitStrategy.SMART)).isSameAs(second);
    }

    @Test
    void getStringCodeShouldResolveThroughSplitStrategyOf() {
        TextSplitter smart = splitter(SplitStrategy.SMART);
        TextSplitter custom = splitter(SplitStrategy.CUSTOM);
        TextSplitterFactory factory = new TextSplitterFactory(List.of(smart, custom));

        assertThat(factory.get("custom")).isSameAs(custom);
        assertThat(factory.get("CUSTOM")).isSameAs(custom);
        assertThat(factory.get("2")).isSameAs(custom);
        assertThat(factory.get("smart")).isSameAs(smart);
        // 未知编码兜底为 SMART
        assertThat(factory.get("whatever")).isSameAs(smart);
        // null 编码兜底为 SMART
        assertThat(factory.get((String) null)).isSameAs(smart);
    }

    @Test
    void getStringCodeShouldThrowWhenResolvedStrategyMissing() {
        TextSplitterFactory factory = new TextSplitterFactory(List.of(splitter(SplitStrategy.CUSTOM)));

        // "smart" 解析为 SMART，但未注册
        assertThatThrownBy(() -> factory.get("smart")).isInstanceOf(IllegalArgumentException.class);
        // 未知编码解析为 SMART，同样未注册
        assertThatThrownBy(() -> factory.get("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullSplitterListShouldFailFastInConstructor() {
        assertThatThrownBy(() -> new TextSplitterFactory(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void retrievedSplitterShouldStillDelegateSplit() {
        TextSplitter smart = splitter(SplitStrategy.SMART);
        List<TextChunk> chunks = List.of(TextChunk.builder().content("a").build());
        when(smart.split("text", 100, 10)).thenReturn(chunks);

        TextSplitterFactory factory = new TextSplitterFactory(List.of(smart));

        assertThat(factory.get(SplitStrategy.SMART).split("text", 100, 10)).isEqualTo(chunks);
    }
}
