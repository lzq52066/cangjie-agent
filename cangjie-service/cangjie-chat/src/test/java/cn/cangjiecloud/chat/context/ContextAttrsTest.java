package cn.cangjiecloud.chat.context;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextAttrs} 常量持有者单元测试：校验约定键字面量与工具类不可实例化。
 */
class ContextAttrsTest {

    @Test
    void constantValuesShouldMatchDocumentedLiterals() {
        assertThat(ContextAttrs.RETRIEVAL_SOURCES).isEqualTo("retrievalSources");
        assertThat(ContextAttrs.REQUEST_CONVERSATION).isEqualTo("requestConversation");
        assertThat(ContextAttrs.HISTORY_MESSAGES).isEqualTo("historyMessages");
        assertThat(ContextAttrs.FRAGMENTS).isEqualTo("contextFragments");
        assertThat(ContextAttrs.APPLICATION_NAME).isEqualTo("applicationName");
        assertThat(ContextAttrs.DESCRIPTION).isEqualTo("description");
        assertThat(ContextAttrs.PROMPT_TEMPLATE_ID).isEqualTo("promptTemplateId");
        assertThat(ContextAttrs.SKILL_IDS).isEqualTo("skillIds");
        assertThat(ContextAttrs.RULE_IDS).isEqualTo("ruleIds");
    }

    @Test
    void allConstantsShouldBeDistinct() {
        assertThat(ContextAttrs.RETRIEVAL_SOURCES)
                .doesNotContain(ContextAttrs.REQUEST_CONVERSATION, ContextAttrs.HISTORY_MESSAGES,
                        ContextAttrs.FRAGMENTS, ContextAttrs.APPLICATION_NAME, ContextAttrs.DESCRIPTION,
                        ContextAttrs.PROMPT_TEMPLATE_ID, ContextAttrs.SKILL_IDS, ContextAttrs.RULE_IDS);
    }

    @Test
    void utilityClassShouldNotBeInstantiable() throws NoSuchMethodException {
        Constructor<ContextAttrs> constructor = ContextAttrs.class.getDeclaredConstructor();
        assertThat(Modifier.isFinal(ContextAttrs.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
