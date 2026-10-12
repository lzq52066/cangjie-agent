package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.core.conversation.ConversationFilter;
import cn.cangjiecloud.core.conversation.ConversationSample;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConversationSourceImpl} 单元测试：问答配对、批量取数不发 N+1、
 * 检索来源解析容错与筛选/限量收敛。
 */
@ExtendWith(MockitoExtension.class)
class ConversationSourceImplTest {

    @Mock
    private IChatMessageService chatMessageService;

    private ConversationSourceImpl source;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.chat.mapper.ChatMessageMapper");
        TableInfoHelper.initTableInfo(assistant, ChatMessageEntity.class);
    }

    @BeforeEach
    void setUp() {
        source = new ConversationSourceImpl(chatMessageService);
    }

    private static ChatMessageEntity message(String id, String role, String content, LocalDateTime createTime) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(id);
        entity.setSessionId("s1");
        entity.setApplicationId("app1");
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreateTime(createTime);
        return entity;
    }

    private static ChatMessageEntity answer(String id, LocalDateTime createTime) {
        return message(id, "assistant", "AI 回答", createTime);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<ChatMessageEntity> lastWrapper(ArgumentCaptor<Wrapper<ChatMessageEntity>> captor) {
        return (LambdaQueryWrapper<ChatMessageEntity>) captor.getValue();
    }

    @Nested
    class LoadSample {

        /** AI 消息：向前找最近一条用户消息配对 */
        @Test
        void shouldPairPrecedingQuestionForAssistantMessage() {
            ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 1));
            reply.setFeedback("dislike");
            reply.setAnnotation("  人工修正  ");
            reply.setRetrievalSources("""
                    [{"documentId":"doc1","paragraphId":"p1"},{"documentId":"doc1","paragraphId":"p2"}]""");
            when(chatMessageService.getById("m1")).thenReturn(reply);
            when(chatMessageService.getOne(any(Wrapper.class), anyBoolean()))
                    .thenReturn(message("q1", "user", "什么是仓颉？", LocalDateTime.of(2026, 10, 9, 10, 0)));

            ConversationSample sample = source.loadSample("m1");

            assertThat(sample.messageId()).isEqualTo("m1");
            assertThat(sample.question()).isEqualTo("什么是仓颉？");
            assertThat(sample.answer()).isEqualTo("AI 回答");
            assertThat(sample.feedback()).isEqualTo("dislike");
            assertThat(sample.expectedAnswer()).isEqualTo("人工修正");
            // 一次检索给出文档与片段两级标注：文档去重，片段是 contextRecall 的唯一依据
            assertThat(sample.referenceDocs()).containsExactly("doc1");
            assertThat(sample.referenceChunks()).containsExactly("p1", "p2");
        }

        /** 用户消息：向后找这一轮的回答，前端在用户气泡上点「加入评测集」也能用 */
        @Test
        void shouldFollowAnswerForUserMessage() {
            ChatMessageEntity question = message("q1", "user", "问题", LocalDateTime.of(2026, 10, 9, 10, 0));
            when(chatMessageService.getById("q1")).thenReturn(question);
            when(chatMessageService.getOne(any(Wrapper.class), anyBoolean()))
                    .thenReturn(answer("m1", LocalDateTime.of(2026, 10, 9, 10, 1)));

            ConversationSample sample = source.loadSample("q1");

            assertThat(sample.messageId()).isEqualTo("m1");
            assertThat(sample.question()).isEqualTo("问题");
        }

        /** 向后找不到回答（还没答完 / 被中断）就没有可回流的样本 */
        @Test
        void shouldReturnNullWhenUserMessageHasNoAnswer() {
            when(chatMessageService.getById("q1"))
                    .thenReturn(message("q1", "user", "问题", LocalDateTime.of(2026, 10, 9, 10, 0)));
            when(chatMessageService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

            assertThat(source.loadSample("q1")).isNull();
        }

        /** system / tool 之类角色不可回流，不做无谓的配对查询 */
        @Test
        void shouldIgnoreOtherRoles() {
            when(chatMessageService.getById("m1"))
                    .thenReturn(message("m1", "system", "系统提示", LocalDateTime.of(2026, 10, 9, 10, 0)));

            assertThat(source.loadSample("m1")).isNull();
            verify(chatMessageService, never()).getOne(any(Wrapper.class), anyBoolean());
        }

        /** 消息不存在与 ID 为空都返回 null：取不到不是异常，交给上层报「消息不存在」 */
        @Test
        void shouldReturnNullWhenMessageMissing() {
            assertThat(source.loadSample(null)).isNull();
            assertThat(source.loadSample("  ")).isNull();
            when(chatMessageService.getById("m-missing")).thenReturn(null);
            assertThat(source.loadSample("m-missing")).isNull();
        }

        /** 坏 JSON 只丢标注不丢样本：回流的问题是真实用户问过的，比标注更值钱 */
        @Test
        void shouldKeepSampleWhenSourcesAreUnparsable() {
            ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 1));
            reply.setRetrievalSources("{这不是 JSON");
            when(chatMessageService.getById("m1")).thenReturn(reply);
            when(chatMessageService.getOne(any(Wrapper.class), anyBoolean()))
                    .thenReturn(message("q1", "user", "问题", LocalDateTime.of(2026, 10, 9, 10, 0)));

            ConversationSample sample = source.loadSample("m1");

            assertThat(sample.question()).isEqualTo("问题");
            assertThat(sample.referenceDocs()).isEmpty();
            assertThat(sample.referenceChunks()).isEmpty();
            assertThat(sample.usable()).isTrue();
        }
    }

    @Nested
    class SearchSamples {

        /** 批量只查一次用户消息：逐条查会变成 N+1，一次点击几百条查询把回流做成压测 */
        @Test
        void shouldLoadQuestionsInOneBatch() {
            ChatMessageEntity first = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 5));
            ChatMessageEntity second = answer("m2", LocalDateTime.of(2026, 10, 9, 9, 5));
            second.setSessionId("s2");
            ChatMessageEntity q2 = message("q2", "user", "问题二", LocalDateTime.of(2026, 10, 9, 9, 0));
            q2.setSessionId("s2");
            when(chatMessageService.list(any(Wrapper.class)))
                    .thenReturn(List.of(first, second))
                    .thenReturn(List.of(
                            message("q1", "user", "问题一", LocalDateTime.of(2026, 10, 9, 10, 0)), q2));
            ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);

            List<ConversationSample> samples = source.searchSamples(ConversationFilter.of(10));

            verify(chatMessageService, times(2)).list(captor.capture());
            // 第二次是按 session 批量捞用户消息，不是每个回答一次
            assertThat(lastWrapper(captor).getTargetSql()).contains("session_id");
            assertThat(lastWrapper(captor).getSqlSegment()).doesNotContain("LIMIT");
            assertThat(samples).hasSize(2);
            assertThat(samples).extracting(ConversationSample::messageId).containsExactly("m1", "m2");
            assertThat(samples).extracting(ConversationSample::question).containsExactly("问题一", "问题二");
        }

        /** 取该回答之前最近的一条用户消息；时间戳相同也算，排除了反而会把上一轮的问题当成本轮的 */
        @Test
        void shouldPickNearestQuestionAtOrBeforeAnswer() {
            ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 0));
            when(chatMessageService.list(any(Wrapper.class)))
                    .thenReturn(List.of(reply))
                    .thenReturn(List.of(
                            message("q1", "user", "最早的问题", LocalDateTime.of(2026, 10, 9, 9, 0)),
                            message("q2", "user", "这一轮的问题", LocalDateTime.of(2026, 10, 9, 10, 0)),
                            message("q3", "user", "更晚的问题", LocalDateTime.of(2026, 10, 9, 11, 0))));

            List<ConversationSample> samples = source.searchSamples(ConversationFilter.of(10));

            assertThat(samples).singleElement()
                    .extracting(ConversationSample::question).isEqualTo("这一轮的问题");
        }

        /** 会话里没有更早的用户消息时照样出样本，由评测侧按「不可用」计数报出 */
        @Test
        void shouldLeaveQuestionNullWhenSessionHasNoEarlierUserMessage() {
            ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 0));
            when(chatMessageService.list(any(Wrapper.class)))
                    .thenReturn(List.of(reply))
                    .thenReturn(List.of());

            List<ConversationSample> samples = source.searchSamples(ConversationFilter.of(10));

            assertThat(samples).singleElement().satisfies(sample -> {
                assertThat(sample.question()).isNull();
                assertThat(sample.usable()).isFalse();
            });
        }

        /** 没有候选时不再去捞用户消息 */
        @Test
        void shouldSkipQuestionLookupWhenNoAnswer() {
            when(chatMessageService.list(any(Wrapper.class))).thenReturn(List.of());

            assertThat(source.searchSamples(null)).isEmpty();
            verify(chatMessageService, times(1)).list(any(Wrapper.class));
        }

        /** 条件为 null 时只拼必加的 role，其余筛选位一个不进 WHERE，并按时间倒序限量 */
        @Test
        void shouldBuildUnrestrictedQueryAndDefaultLimit() {
            when(chatMessageService.list(any(Wrapper.class))).thenReturn(List.of());
            ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);

            source.searchSamples(null);

            verify(chatMessageService).list(captor.capture());
            LambdaQueryWrapper<ChatMessageEntity> wrapper = lastWrapper(captor);
            assertThat(wrapper.getSqlSegment())
                    .contains("role")
                    .doesNotContain("application_id")
                    .doesNotContain("feedback")
                    .doesNotContain("annotation");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY create_time DESC");
            assertThat(wrapper.getTargetSql()).contains("LIMIT 50");
        }

        /** 硬上限收敛：一次灌几千条既烧 token 又让「回归集」失焦 */
        @Test
        void shouldCapLimit() {
            when(chatMessageService.list(any(Wrapper.class))).thenReturn(List.of());
            ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);

            source.searchSamples(new ConversationFilter(null, null, null, 100000));

            verify(chatMessageService).list(captor.capture());
            assertThat(lastWrapper(captor).getTargetSql()).contains("LIMIT 200");
        }

        /** 应用与反馈条件按需拼进 WHERE，非空才加 */
        @Test
        void shouldApplyApplicationAndFeedbackFilter() {
            when(chatMessageService.list(any(Wrapper.class))).thenReturn(List.of());
            ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);

            source.searchSamples(new ConversationFilter("app1", "dislike", null, 10));

            verify(chatMessageService).list(captor.capture());
            String sql = lastWrapper(captor).getSqlSegment();
            assertThat(sql).contains("application_id");
            assertThat(sql).contains("feedback");
        }

        /**
         * 标注三态：TRUE 只要有标注、FALSE 只要没标注、null 不限 ——
         * 拿 false 当缺省哨兵会把「未标注的也要」和「都无所谓」混成一格。
         */
        @Test
        void shouldDistinguishAnnotatedOnlyStates() {
            when(chatMessageService.list(any(Wrapper.class))).thenReturn(List.of());
            ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);

            source.searchSamples(new ConversationFilter(null, null, Boolean.TRUE, 10));
            verify(chatMessageService).list(captor.capture());
            assertThat(lastWrapper(captor).getSqlSegment())
                    .contains("annotation IS NOT NULL")
                    .contains("annotation <>");

            captor = ArgumentCaptor.forClass(Wrapper.class);
            source.searchSamples(new ConversationFilter(null, null, Boolean.FALSE, 10));
            verify(chatMessageService, times(2)).list(captor.capture());
            assertThat(lastWrapper(captor).getSqlSegment()).contains("annotation IS NULL");

            captor = ArgumentCaptor.forClass(Wrapper.class);
            source.searchSamples(new ConversationFilter(null, null, null, 10));
            verify(chatMessageService, times(3)).list(captor.capture());
            assertThat(lastWrapper(captor).getSqlSegment()).doesNotContain("annotation");
        }

        /** 入参 ID 的空白与 null 元素由 record 归一，实现方不必再防一遍 */
        @Test
        void shouldNormalizeIdsFromDirtySources() {
            ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 1));
            reply.setRetrievalSources("""
                    [{"documentId":" doc1 "},{"documentId":null},{"paragraphId":"p1"},null]""");
            when(chatMessageService.getById("m1")).thenReturn(reply);
            when(chatMessageService.getOne(any(Wrapper.class), anyBoolean()))
                    .thenReturn(message("q1", "user", "问题", LocalDateTime.of(2026, 10, 9, 10, 0)));

            ConversationSample sample = source.loadSample("m1");

            assertThat(sample.referenceDocs()).containsExactly("doc1");
            assertThat(sample.referenceChunks()).containsExactly("p1");
        }
    }

    /** 单条取数的定序：同一时刻落库的消息拿雪花 ID 兜底 */
    @Test
    void shouldBreakTiesByIdWhenPairingSingleMessage() {
        ChatMessageEntity reply = answer("m1", LocalDateTime.of(2026, 10, 9, 10, 0));
        when(chatMessageService.getById("m1")).thenReturn(reply);
        when(chatMessageService.getOne(any(Wrapper.class), anyBoolean()))
                .thenReturn(message("q1", "user", "问题", LocalDateTime.of(2026, 10, 9, 10, 0)));

        source.loadSample("m1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ChatMessageEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chatMessageService).getOne(captor.capture(), anyBoolean());
        String sql = ((LambdaQueryWrapper<ChatMessageEntity>) captor.getValue()).getSqlSegment();
        assertThat(sql).containsIgnoringCase("ORDER BY create_time DESC,id DESC");
        assertThat(sql).contains("role");
    }
}
