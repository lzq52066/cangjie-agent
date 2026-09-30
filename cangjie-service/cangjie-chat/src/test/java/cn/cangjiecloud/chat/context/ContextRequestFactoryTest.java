package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ContextRequestFactory} 单元测试：业务实体 + 入参翻译为 {@link ContextRequest}，
 * 含 JSON 数组字段解析与 OpenAI 兼容会话标记。
 */
class ContextRequestFactoryTest {

    private HarnessConfigResolver configResolver;
    private ContextRequestFactory factory;

    @BeforeEach
    void setUp() {
        configResolver = mock(HarnessConfigResolver.class);
        factory = new ContextRequestFactory(configResolver);
    }

    private ApplicationEntity application() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId("app-1");
        application.setName("智能助手");
        application.setDescription("一个乐于助人的助手");
        application.setModelId("model-1");
        application.setPromptTemplateId("tpl-1");
        application.setKnowledgeBaseIds("[\"kb-1\", \"kb-2\"]");
        application.setSkillIds("[\"skill-1\"]");
        application.setRuleIds("[\"rule-1\"]");
        application.setMaxTurns(6);
        application.setRagMode("simple");
        application.setMemoryEnabled(true);
        return application;
    }

    @Test
    void shouldTranslateApplicationAndRequestIntoContextRequest() {
        HarnessConfig config = HarnessConfig.builder().build();
        when(configResolver.parse(org.mockito.ArgumentMatchers.any())).thenReturn(config);

        ContextRequest request = factory.newRequest(application(), "sess-1", "user-1", "你好",
                null, null, "trace-1");

        assertThat(request.getApplicationId()).isEqualTo("app-1");
        assertThat(request.getSessionId()).isEqualTo("sess-1");
        assertThat(request.getUserId()).isEqualTo("user-1");
        assertThat(request.getModelId()).isEqualTo("model-1");
        assertThat(request.getUserQuery()).isEqualTo("你好");
        assertThat(request.getKnowledgeBaseIds()).containsExactly("kb-1", "kb-2");
        assertThat(request.getMaxTurns()).isEqualTo(6);
        assertThat(request.getRagMode()).isEqualTo("simple");
        assertThat(request.isMemoryEnabled()).isTrue();
        assertThat(request.getTraceId()).isEqualTo("trace-1");
        assertThat(request.getConfig()).isSameAs(config);

        Map<String, Object> attributes = request.getAttributes();
        assertThat(attributes.get(ContextAttrs.APPLICATION_NAME)).isEqualTo("智能助手");
        assertThat(attributes.get(ContextAttrs.DESCRIPTION)).isEqualTo("一个乐于助人的助手");
        assertThat(attributes.get(ContextAttrs.PROMPT_TEMPLATE_ID)).isEqualTo("tpl-1");
        assertThat(attributes.get(ContextAttrs.SKILL_IDS)).isEqualTo(List.of("skill-1"));
        assertThat(attributes.get(ContextAttrs.RULE_IDS)).isEqualTo(List.of("rule-1"));
        assertThat(attributes.get(ContextAttrs.RETRIEVAL_SOURCES)).isInstanceOf(List.class);
    }

    @Test
    void shouldReuseProvidedRetrievalSourcesList() {
        List<Map<String, Object>> sources = new java.util.ArrayList<>();

        ContextRequest request = factory.newRequest(application(), "s", "u", "m", null, sources, "t");

        assertThat(request.getAttributes().get(ContextAttrs.RETRIEVAL_SOURCES)).isSameAs(sources);
    }

    @Test
    void shouldMarkRequestConversationWhenMessagesProvided() {
        List<ChatRequestDTO.ConversationMessage> messages = List.of(
                message("system", "你是助手"), message("User", "你好"));

        ContextRequest request = factory.newRequest(application(), "s", "u", "你好", messages, null, "t");

        @SuppressWarnings("unchecked")
        List<cn.cangjiecloud.core.model.ChatMessage> conversation =
                (List<cn.cangjiecloud.core.model.ChatMessage>)
                        request.getAttributes().get(ContextAttrs.REQUEST_CONVERSATION);
        assertThat(conversation).hasSize(2);
        assertThat(conversation.get(1).getRole()).isEqualTo("user");
    }

    @Test
    void shouldIgnoreBlankConversationMessages() {
        List<ChatRequestDTO.ConversationMessage> messages = List.of(
                message("", "无角色"), message("user", "   "), message("user", "有效"));

        ContextRequest request = factory.newRequest(application(), "s", "u", "m", messages, null, "t");

        @SuppressWarnings("unchecked")
        List<cn.cangjiecloud.core.model.ChatMessage> conversation =
                (List<cn.cangjiecloud.core.model.ChatMessage>)
                        request.getAttributes().get(ContextAttrs.REQUEST_CONVERSATION);
        assertThat(conversation).hasSize(1);
    }

    private static ChatRequestDTO.ConversationMessage message(String role, String content) {
        ChatRequestDTO.ConversationMessage message = new ChatRequestDTO.ConversationMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    @Nested
    class ParseIdList {

        @Test
        void shouldParseValidJsonArray() {
            assertThat(ContextRequestFactory.parseIdList("[\"a\", \"b\"]"))
                    .containsExactly("a", "b");
        }

        @Test
        void shouldReturnEmptyForNullOrBlank() {
            assertThat(ContextRequestFactory.parseIdList(null)).isEmpty();
            assertThat(ContextRequestFactory.parseIdList("  ")).isEmpty();
        }

        @Test
        void shouldSkipNullAndBlankElements() {
            assertThat(ContextRequestFactory.parseIdList("[null, \"\", \"x\"]"))
                    .containsExactly("x");
        }

        @Test
        void shouldReturnEmptyForInvalidJson() {
            assertThat(ContextRequestFactory.parseIdList("not-json")).isEmpty();
        }
    }
}
