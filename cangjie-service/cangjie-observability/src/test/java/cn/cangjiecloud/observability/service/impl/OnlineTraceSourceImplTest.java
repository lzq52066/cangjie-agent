package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.core.eval.OnlineTraceSample;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.LlmTraceMapper;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OnlineTraceSourceImpl} 单元测试。
 * <p>
 * 这里盯的是三件一旦写错就会静默烧钱或静默失真的事：待评条件（必须排掉评测自己产出的调用，
 * 且取数与计数同源）、样本还原（检索载荷优先、prompt 兜底、片段去重、脏载荷不炸整批）、
 * 回填条件（必须带「仍未被标过」，否则并发撞车会把同一条评两遍）。两个 Mapper 全 mock。
 */
@ExtendWith(MockitoExtension.class)
class OnlineTraceSourceImplTest {

    @Mock
    private LlmTraceMapper llmTraceMapper;
    @Mock
    private TraceRecordMapper traceRecordMapper;

    private OnlineTraceSourceImpl source;

    @BeforeAll
    static void installTableInfoCache() {
        // Wrapper 的 lambda 列名要靠 TableInfo 缓存解析，不连库的单元测试得自己灌
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant llmTrace = new MapperBuilderAssistant(configuration, "");
        llmTrace.setCurrentNamespace(LlmTraceMapper.class.getName());
        TableInfoHelper.initTableInfo(llmTrace, LlmTraceEntity.class);

        MapperBuilderAssistant traceRecord = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        traceRecord.setCurrentNamespace(TraceRecordMapper.class.getName());
        TableInfoHelper.initTableInfo(traceRecord, TraceRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        source = new OnlineTraceSourceImpl(llmTraceMapper, traceRecordMapper);
    }

    private static LlmTraceEntity row(String id, String traceId, String prompt, String response) {
        LlmTraceEntity entity = new LlmTraceEntity();
        entity.setId(id);
        entity.setTraceId(traceId);
        entity.setSessionId("session-1");
        entity.setAppId("app-1");
        entity.setModelName("gpt-4o");
        entity.setPromptContent(prompt);
        entity.setResponseContent(response);
        return entity;
    }

    private static TraceRecordEntity span(String traceId, String input, String output) {
        TraceRecordEntity span = new TraceRecordEntity();
        span.setTraceId(traceId);
        span.setInputPayload(input);
        span.setOutputPayload(output);
        return span;
    }

    /** 捕获交给 llmTraceMapper.selectList 的查询条件 */
    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<LlmTraceEntity> captureFetchWrapper() {
        ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(llmTraceMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<LlmTraceEntity>) captor.getValue();
    }

    @Nested
    class PendingCondition {

        @Test
        void shouldExcludeEvalOwnCallsButKeepNullTraceId() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of());

            source.fetchUnscored(10);

            String sql = captureFetchWrapper().getTargetSql();
            assertThat(sql).contains("status", "evaluated", "trace_id");
            // 判定调用自己也落 llm_trace 且 evaluated=false：不排掉就会把判定输出当线上问答再评一遍
            assertThat(sql).containsIgnoringCase("IS NULL").containsIgnoringCase("NOT LIKE");
            assertThat(captureFetchWrapper().getParamNameValuePairs().values())
                    .contains("success", "eval-%");
        }

        @Test
        void shouldOnlySelectColumnsNeededForScoring() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of());

            source.fetchUnscored(10);

            String select = captureFetchWrapper().getSqlSelect();
            assertThat(select).contains("prompt_content", "response_content", "trace_id", "start_time");
            // 不选 cost / tokens / ttft：捞一批样本没必要把整行拖回来
            assertThat(select).doesNotContain("cost", "ttft");
        }

        @Test
        void countShouldUseTheSameConditionAsFetch() {
            // 取数与计数同源：条件不一致时面板上的积压水位永远降不下去（数得到、捞不着）
            when(llmTraceMapper.selectList(any())).thenReturn(List.of());
            source.fetchUnscored(10);
            String fetchSql = captureFetchWrapper().getTargetSql();
            var fetchParams = captureFetchWrapper().getParamNameValuePairs().values();

            when(llmTraceMapper.selectCount(any())).thenReturn(7L);
            assertThat(source.countUnscored()).isEqualTo(7L);

            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(llmTraceMapper).selectCount(captor.capture());
            LambdaQueryWrapper<LlmTraceEntity> countWrapper =
                    (LambdaQueryWrapper<LlmTraceEntity>) captor.getValue();

            // 计数那一趟不带 order by / limit，因此比的是「条件部分一致」而不是整个 SQL 串
            assertThat(countWrapper.getTargetSql()).contains(
                    "status", "evaluated", "trace_id", "IS NULL", "NOT LIKE");
            assertThat(fetchSql).contains(
                    "status", "evaluated", "trace_id", "IS NULL", "NOT LIKE");
            assertThat(countWrapper.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrderElementsOf(fetchParams);
        }

        @Test
        void shouldReturnZeroWhenCountMisses() {
            when(llmTraceMapper.selectCount(any())).thenReturn(null);

            assertThat(source.countUnscored()).isZero();
        }
    }

    @Nested
    class LimitClamp {

        @Test
        void shouldClampLimitIntoRange() {
            // limit 是拼进 SQL 的，不能有第二个来源：上限夹到 200、下限夹到 1
            when(llmTraceMapper.selectList(any())).thenReturn(List.of());
            source.fetchUnscored(9999);
            source.fetchUnscored(0);
            source.fetchUnscored(-3);

            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(llmTraceMapper, times(3)).selectList(captor.capture());
            List<String> sql = captor.getAllValues().stream()
                    .map(wrapper -> ((LambdaQueryWrapper<?>) wrapper).getTargetSql())
                    .toList();

            assertThat(sql.get(0)).contains("limit 200");
            assertThat(sql.get(1)).contains("limit 1");
            assertThat(sql.get(2)).contains("limit 1");
        }
    }

    @Nested
    class SampleRebuild {

        @Test
        void shouldPreferRetrievalPayloadOverPrompt() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of(
                    row("t-1", "trace-1", "[{\"type\":\"user\",\"text\":\"寒暄\"}]", "回答正文")));
            when(traceRecordMapper.selectList(any())).thenReturn(List.of(
                    // 原始 query 优先：改写后的口径不是用户那一问
                    span("trace-1", "{\"query\":\"退货规定\",\"effectiveQuery\":\"退换货政策\"}",
                            // 同段落两次召回只留一份：多次检索的并集才是模型当时看得到的上下文
                            "[{\"content\":\"片段A\",\"paragraphId\":\"p1\"},"
                                    + "{\"content\":\"片段A 的第二次召回\",\"paragraphId\":\"p1\"}]")));

            List<OnlineTraceSample> samples = source.fetchUnscored(10);

            assertThat(samples).hasSize(1);
            OnlineTraceSample sample = samples.get(0);
            assertThat(sample.id()).isEqualTo("t-1");
            assertThat(sample.query()).isEqualTo("退货规定");
            assertThat(sample.answer()).isEqualTo("回答正文");
            assertThat(sample.modelName()).isEqualTo("gpt-4o");
            assertThat(sample.contexts()).hasSize(1);
            assertThat(sample.contexts().get(0).text()).isEqualTo("片段A");
            assertThat(sample.contexts().get(0).paragraphId()).isEqualTo("p1");
            // 线上样本没有标注：期望回答与 ground truth 一律留空，让指标自己决定不评什么
            assertThat(sample.toEvalSample().expectedAnswer()).isNullOrEmpty();
            assertThat(sample.scoreable()).isTrue();
        }

        @Test
        void shouldFallBackToEffectiveQueryThenLastUserMessage() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of(
                    row("t-1", "trace-1", "[{\"type\":\"system\",\"text\":\"介绍\"},{\"type\":\"user\",\"text\":\"第一问\"},{\"type\":\"ai\",\"text\":\"答\"},{\"type\":\"user\",\"text\":\"要判定的那一问\"}]", "回答"),
                    row("t-2", "trace-2", "[{\"type\":\"USER\",\"contents\":[{\"type\":\"text\",\"text\":\"新版格式\"}]}]", "回答2")));
            // 只有改写后的 query：兜底用它，总比从 prompt 里猜强
            when(traceRecordMapper.selectList(any())).thenReturn(List.of(
                    span("trace-1", "{\"effectiveQuery\":\"改写后的问题\"}", "[]")));

            List<OnlineTraceSample> samples = source.fetchUnscored(10);

            assertThat(samples).extracting(OnlineTraceSample::query)
                    // 多轮对话取最后一条用户消息：第一条往往是寒暄，被判定的那一问在末尾
                    .containsExactly("改写后的问题", "新版格式");
            // 第二条没有检索留痕（载荷开关可能关着），样本照发，由指标按「缺片段就不出片段类的分」处理
            assertThat(samples.get(1).contexts()).isEmpty();
        }

        @Test
        void shouldDedupeChunksWithoutParagraphIdAndSkipBlankContent() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of(
                    row("t-1", "trace-1", null, "回答")));
            when(traceRecordMapper.selectList(any())).thenReturn(List.of(
                    span("trace-1", "not-json", "[{\"content\":\"同一段\"},{\"content\":\"同一段\"},"
                            + "{\"content\":\"\",\"paragraphId\":\"p9\"},{\"content\":\"另一段\"}]")));

            List<OnlineTraceSample> samples = source.fetchUnscored(10);

            // 没有段落 ID 的按正文去重
            assertThat(samples.get(0).contexts()).extracting(chunk -> chunk.text())
                    .containsExactly("同一段", "另一段");
            // 脏 input 不炸样本：解析不出问题就退回 prompt，这里 prompt 也没有，保持空
            assertThat(samples.get(0).query()).isNull();
        }

        @Test
        void shouldSkipRetrievalLookupWhenNoRowsOrNoTraceId() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of());

            assertThat(source.fetchUnscored(10)).isEmpty();
            // 没有 traceId 就不必去链路表捞一趟
            verify(traceRecordMapper, never()).selectList(any());

            when(llmTraceMapper.selectList(any())).thenReturn(List.of(row("t-1", null, null, "回答")));
            assertThat(source.fetchUnscored(10)).singleElement()
                    .satisfies(sample -> assertThat(sample.scoreable()).isTrue());
            verify(traceRecordMapper, never()).selectList(any());
        }

        @Test
        void shouldTreatBlankAnswerAsUnscoreable() {
            when(llmTraceMapper.selectList(any())).thenReturn(List.of(row("t-1", null, null, "  ")));

            assertThat(source.fetchUnscored(10)).singleElement()
                    .satisfies(sample -> assertThat(sample.scoreable()).isFalse());
        }
    }

    @Nested
    class MarkScored {

        @SuppressWarnings("unchecked")
        private LambdaUpdateWrapper<LlmTraceEntity> captureUpdateWrapper() {
            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(llmTraceMapper).update(any(), captor.capture());
            return (LambdaUpdateWrapper<LlmTraceEntity>) captor.getValue();
        }

        @Test
        void shouldCarryUnscoredGuardInWhereClause() {
            when(llmTraceMapper.update(any(), any())).thenReturn(1);

            assertThat(source.markScored("t-1", Map.of("faithfulness", 0.8))).isTrue();

            LambdaUpdateWrapper<LlmTraceEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet()).contains("evaluated", "eval_scores", "update_time");
            // 条件里必须再判一次 evaluated=false：定时任务与手动触发撞上时只有一边写得进去
            assertThat(wrapper.getTargetSql()).contains("id =").contains("evaluated =");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("t-1");
        }

        @Test
        void shouldWriteNullScoresWhenNothingScored() {
            // 一个指标都没出的样本仍要标已评：不然每轮取到的都是最新这几十条，积压推不动
            when(llmTraceMapper.update(any(), any())).thenReturn(1);

            assertThat(source.markScored("t-1", Map.of())).isTrue();

            LambdaUpdateWrapper<LlmTraceEntity> wrapper = captureUpdateWrapper();
            assertThat(wrapper.getSqlSet()).contains("eval_scores");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .noneMatch(value -> String.valueOf(value).startsWith("{"));
        }

        @Test
        void shouldReportLostRaceAsFalse() {
            when(llmTraceMapper.update(any(), any())).thenReturn(0);

            assertThat(source.markScored("t-1", Map.of("faithfulness", 0.5))).isFalse();
        }

        @Test
        void shouldRejectBlankIdWithoutTouchingDatabase() {
            assertThat(source.markScored(null, Map.of("a", 1.0))).isFalse();
            assertThat(source.markScored("  ", Map.of("a", 1.0))).isFalse();
            verify(llmTraceMapper, never()).update(any(), any());
        }
    }
}
