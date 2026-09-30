package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SessionSummaryContributor} 单元测试：会话摘要产出 SYSTEM_PRE 片段，OpenAI 路径让位。
 */
class SessionSummaryContributorTest {

    private IChatSessionService chatSessionService;
    private SessionSummaryContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        chatSessionService = mock(IChatSessionService.class);
        contributor = new SessionSummaryContributor(chatSessionService);
        attributes = new HashMap<>();
    }

    private ContextRequest request() {
        return ContextRequest.builder().sessionId("sess-1").attributes(attributes).build();
    }

    @Test
    void metadataShouldDeclareSystemPreSlotAndOrder50() {
        assertThat(contributor.name()).isEqualTo("sessionSummary");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.SYSTEM_PRE);
        assertThat(contributor.getOrder()).isEqualTo(50);
    }

    @Test
    void supportsShouldBeTrueForInternalPathWithSession() {
        assertThat(contributor.supports(request())).isTrue();
    }

    @Test
    void supportsShouldBeFalseWhenSessionIdBlank() {
        ContextRequest request = ContextRequest.builder().attributes(attributes).build();
        assertThat(contributor.supports(request)).isFalse();
    }

    @Test
    void supportsShouldBeFalseForRequestConversationPath() {
        attributes.put(ContextAttrs.REQUEST_CONVERSATION, java.util.List.of());
        assertThat(contributor.supports(request())).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenSessionMissing() {
        when(chatSessionService.getBySessionId("sess-1")).thenReturn(null);
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenSummaryBlank() {
        when(chatSessionService.getBySessionId("sess-1")).thenReturn(new ChatSessionEntity());
        assertThat(contributor.contribute(request()).isEmpty()).isTrue();
    }

    @Test
    void shouldProduceSystemPreFragmentWithSummary() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setSummary("早前讨论了架构方案");
        when(chatSessionService.getBySessionId("sess-1")).thenReturn(session);

        ContextFragment fragment = contributor.contribute(request());

        assertThat(fragment.getSlot()).isEqualTo(ContextSlot.SYSTEM_PRE);
        assertThat(fragment.getSource()).isEqualTo("sessionSummary");
        assertThat(fragment.getMessages().get(0).getContent()).contains("会话摘要").contains("架构方案");
    }
}
