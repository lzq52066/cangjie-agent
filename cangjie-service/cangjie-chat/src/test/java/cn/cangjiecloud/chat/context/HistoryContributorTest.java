package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HistoryContributor} 单元测试：OpenAI 兼容路径接管请求会话，内部路径查库并按 token 预算截断。
 */
class HistoryContributorTest {

    private IChatMessageService chatMessageService;
    private HistoryContributor contributor;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        chatMessageService = mock(IChatMessageService.class);
        contributor = new HistoryContributor(chatMessageService);
        ReflectionTestUtils.setField(contributor, "historyMaxTokens", 6000);
        attributes = new HashMap<>();
    }

    private ContextRequest request() {
        return ContextRequest.builder().sessionId("sess-1").attributes(attributes).build();
    }

    private static ChatMessageEntity messageEntity(String role, String content, LocalDateTime createTime) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreateTime(createTime);
        return entity;
    }

    @Test
    void metadataShouldDeclareHistorySlotAndOrder150() {
        assertThat(contributor.name()).isEqualTo("history");
        assertThat(contributor.slot()).isEqualTo(ContextSlot.HISTORY);
        assertThat(contributor.getOrder()).isEqualTo(150);
    }

    @Nested
    class RequestConversationPath {

        @Test
        void shouldTakeOverRequestConversationAndPublishHistoryAttribute() {
            List<ChatMessage> conversation = List.of(
                    ChatMessage.system("sys"), ChatMessage.user("hi"));
            attributes.put(ContextAttrs.REQUEST_CONVERSATION, conversation);

            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.getMessages()).hasSize(2);
            assertThat(fragment.getSource()).isEqualTo("history");
            assertThat(attributes.get(ContextAttrs.HISTORY_MESSAGES)).isSameAs(conversation);
        }

        @Test
        void shouldFallBackToDbWhenRequestConversationIsEmpty() {
            attributes.put(ContextAttrs.REQUEST_CONVERSATION, List.of());
            when(chatMessageService.list(Mockito.<Wrapper<ChatMessageEntity>>any())).thenReturn(List.of());

            assertThat(contributor.contribute(request()).getMessages()).isEmpty();
        }
    }

    @Nested
    class DbPath {

        @Test
        void shouldReturnEmptyWhenSessionIdBlank() {
            ContextRequest noSession = ContextRequest.builder().attributes(attributes).build();

            ContextFragment fragment = contributor.contribute(noSession);

            assertThat(fragment.getMessages()).isEmpty();
            assertThat(attributes.get(ContextAttrs.HISTORY_MESSAGES)).isNotNull();
        }

        @Test
        void shouldReverseDbOrderingIntoChronologicalOrder() {
            LocalDateTime t = LocalDateTime.now();
            List<ChatMessageEntity> reversed = List.of(
                    messageEntity("assistant", "回复", t.plusSeconds(2)),
                    messageEntity("user", "提问", t.plusSeconds(1)));
            when(chatMessageService.list(Mockito.<Wrapper<ChatMessageEntity>>any())).thenReturn(reversed);

            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.getMessages()).extracting(ChatMessage::getRole)
                    .containsExactly("user", "assistant");
            assertThat(fragment.getMessages()).extracting(ChatMessage::getContent)
                    .containsExactly("提问", "回复");
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldUseMaxTurnsToComputeQueryLimit() {
            when(chatMessageService.list(Mockito.<Wrapper<ChatMessageEntity>>any())).thenAnswer(inv -> {
                Wrapper<ChatMessageEntity> wrapper = inv.getArgument(0);
                // 无 MyBatis 上下文时 getSqlSegment() 取不到 lambda 缓存，直接读拼接的 lastSql
                SharedString lastSql = (SharedString) ReflectionTestUtils.getField(wrapper, "lastSql");
                // MyBatis-Plus 会在 lastSql 前拼接空格
                assertThat(lastSql.getStringValue().trim()).isEqualTo("LIMIT 7");
                return new ArrayList<ChatMessageEntity>();
            });
            ContextRequest request = ContextRequest.builder().sessionId("sess-1").maxTurns(3)
                    .attributes(attributes).build();

            contributor.contribute(request);
        }

        @Test
        void shouldTruncateOldestMessagesWhenTokenBudgetExceeded() {
            ReflectionTestUtils.setField(contributor, "historyMaxTokens", 10);
            LocalDateTime t = LocalDateTime.now();
            List<ChatMessageEntity> reversed = List.of(
                    messageEntity("assistant", "最新的一条回复内容", t.plusSeconds(3)),
                    messageEntity("user", "中间的一条提问", t.plusSeconds(2)),
                    messageEntity("assistant", "非常非常非常非常非常旧的回复", t.plusSeconds(1)));
            when(chatMessageService.list(Mockito.<Wrapper<ChatMessageEntity>>any())).thenReturn(reversed);

            ContextFragment fragment = contributor.contribute(request());

            assertThat(fragment.getMessages()).last()
                    .satisfies(m -> assertThat(m.getContent()).isEqualTo("最新的一条回复内容"));
            assertThat(fragment.getMessages()).hasSizeLessThan(3);
        }

        @Test
        void shouldKeepAllMessagesWithinBudget() {
            ReflectionTestUtils.setField(contributor, "historyMaxTokens", 0);
            LocalDateTime t = LocalDateTime.now();
            when(chatMessageService.list(Mockito.<Wrapper<ChatMessageEntity>>any())).thenReturn(List.of(
                    messageEntity("user", "a", t), messageEntity("assistant", "b", t.plusSeconds(1))));

            assertThat(contributor.contribute(request()).getMessages()).hasSize(2);
        }
    }
}
