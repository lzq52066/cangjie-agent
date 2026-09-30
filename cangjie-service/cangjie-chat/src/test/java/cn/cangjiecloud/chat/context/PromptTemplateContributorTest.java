package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.prompt.service.PromptCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PromptTemplateContributor} 单元测试：模板 ID 存在且缓存服务命中时产出 SYSTEM 片段。
 */
class PromptTemplateContributorTest {

    private PromptCacheService promptCacheService;
    private PromptTemplateContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        promptCacheService = mock(PromptCacheService.class);
        contributor = new PromptTemplateContributor(promptCacheService);
        attributes = new HashMap<>();
    }

    private ContextRequest request() {
        return ContextRequest.builder().attributes(attributes).build();
    }

    @Test
    void metadataShouldDeclareSystemSlot() {
        assertThat(contributor.name()).isEqualTo("promptTemplate");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.SYSTEM);
    }

    @Test
    void shouldReturnEmptyWhenTemplateIdMissing() {
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenTemplateIdBlank() {
        attributes.put(ContextAttrs.PROMPT_TEMPLATE_ID, "  ");
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenCacheMisses() {
        attributes.put(ContextAttrs.PROMPT_TEMPLATE_ID, "tpl-1");
        when(promptCacheService.getContent("tpl-1")).thenReturn(null);

        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldProduceSystemFragmentWhenCacheHits() {
        attributes.put(ContextAttrs.PROMPT_TEMPLATE_ID, "tpl-1");
        when(promptCacheService.getContent("tpl-1")).thenReturn("你是一个严谨的助手");

        ContextFragment fragment = contributor.contribute(request());

        assertThat(fragment.getSlot()).isEqualTo(ContextSlot.SYSTEM);
        assertThat(fragment.getSource()).isEqualTo("promptTemplate");
        assertThat(fragment.getMessages()).hasSize(1);
        assertThat(fragment.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(fragment.getMessages().get(0).getContent()).isEqualTo("你是一个严谨的助手");
    }
}
