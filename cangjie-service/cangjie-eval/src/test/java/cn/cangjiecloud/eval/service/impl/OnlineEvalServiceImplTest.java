package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.eval.ContextChunk;
import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import cn.cangjiecloud.core.eval.OnlineTraceSample;
import cn.cangjiecloud.core.eval.OnlineTraceSource;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.eval.dto.OnlineEvalRequestDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalSummaryDTO;
import cn.cangjiecloud.eval.metric.EvalMetricRegistry;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OnlineEvalServiceImpl} 单元测试。
 * <p>
 * 盯的是几个会把钱烧掉或把水位算歪的点：没接上样本来源时必须显式不可用（而不是回一个
 * 看起来「线上没问题」的空摘要）、判定模型要在开跑前解析好、判定调用的 traceId 必须带
 * {@code eval-} 前缀（否则下一轮会把判定输出当线上问答再评一遍）、没出分的样本仍要标已评
 * （不然积压推不动）、抢标失败的分数不能进均值。指标注册表与模型客户端全 mock。
 */
@ExtendWith(MockitoExtension.class)
class OnlineEvalServiceImplTest {

    @Mock
    private EvalMetricRegistry metricRegistry;
    @Mock
    private IModelService modelService;
    @Mock
    private OnlineTraceSource traceSource;
    @Mock
    private OpenAICompatibleClient modelClient;

    private OnlineEvalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OnlineEvalServiceImpl(metricRegistry, modelService);
        // 字段注入的可选依赖：单元测试里手动挂上，没挂的那条分支由 disableSource 单独覆盖
        ReflectionTestUtils.setField(service, "traceSource", traceSource);
    }

    private static OnlineTraceSample sample(String id, String answer) {
        return new OnlineTraceSample(id, "trace-" + id, "session-1", "app-1", "gpt-4o",
                "退货规定是什么", answer,
                List.of(new ContextChunk("七天内可退", "p1", "d1")), LocalDateTime.now());
    }

    private static MetricScore score(double value) {
        return MetricScore.of(value, 3);
    }

    /** 假指标：注册表在这里是 mock，这些实例只会当作「选中了哪几个指标」的凭据传递 */
    private void stubMetrics(int count) {
        List<EvalMetric> metrics = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = "m" + i;
            metrics.add(new EvalMetric() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public MetricScore score(EvalSample sample, MetricContext context) {
                    return MetricScore.of(1.0);
                }
            });
        }
        when(metricRegistry.resolve(any())).thenReturn(metrics);
    }

    private void stubJudgeModel() {
        ModelEntity defaultModel = new ModelEntity();
        defaultModel.setId("m-default");
        lenient().when(modelService.getDefaultModel()).thenReturn(defaultModel);
        lenient().when(modelService.getClient(anyString())).thenReturn(modelClient);
    }

    @Nested
    class Availability {

        @Test
        void shouldFailLoudlyWhenSourceNotWired() {
            // 没挂上来源时整体不可用：空摘要看起来像「线上没有问题」，实际是「压根没评」
            ReflectionTestUtils.setField(service, "traceSource", null);
            assertThatThrownBy(() -> service.score(new OnlineEvalRequestDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("在线评测不可用");
            // 积压水位则按 0 报：读数是「没接入」而不是「报错」，面板不该红一片
            assertThat(service.pendingCount()).isZero();
        }

        @Test
        void shouldDelegatePendingCount() {
            when(traceSource.countUnscored()).thenReturn(128L);

            assertThat(service.pendingCount()).isEqualTo(128L);
        }
    }

    @Nested
    class EarlyExits {

        @Test
        void shouldNotResolveJudgeWhenThereIsNothingToScore() {
            when(traceSource.countUnscored()).thenReturn(0L);
            when(traceSource.fetchUnscored(20)).thenReturn(List.of());
            stubMetrics(1);

            OnlineEvalSummaryDTO summary = service.score(null);

            assertThat(summary.getPendingBefore()).isZero();
            assertThat(summary.getSampled()).isZero();
            assertThat(summary.getMetrics()).isEmpty();
            // 没样本就不去解析判定模型：一趟空转没必要把「未配置默认模型」报成评测失败
            verify(modelService, never()).getClient(anyString());
        }

        @Test
        void shouldNotTouchSamplesWhenNoMetricResolved() {
            // 指标名全拼错时早退：没有判定调用，却把样本标成「评过但没分」是最坏的结果
            when(traceSource.countUnscored()).thenReturn(9L);
            when(traceSource.fetchUnscored(5)).thenReturn(List.of(sample("t1", "回答")));
            when(metricRegistry.resolve(any())).thenReturn(List.of());

            OnlineEvalSummaryDTO summary = service.score(request(5, null, List.of("typo")));

            assertThat(summary.getSampled()).isEqualTo(1);
            assertThat(summary.getScored()).isZero();
            verify(traceSource, never()).markScored(anyString(), any());
        }

        @Test
        void shouldClampLimit() {
            when(traceSource.countUnscored()).thenReturn(0L);
            when(traceSource.fetchUnscored(anyInt())).thenReturn(List.of());
            stubMetrics(1);

            service.score(request(9999, null, null));
            // 单轮上限 200：在线评测每条样本要跑好几轮判定调用，条数直接等于花多少钱
            verify(traceSource).fetchUnscored(200);

            service.score(request(0, null, null));
            verify(traceSource).fetchUnscored(1);
        }
    }

    @Nested
    class Scoring {

        @Test
        void shouldAverageOnlyEvaluatedMetricsAndChargeJudgeTokens() {
            stubJudgeModel();
            stubMetrics(2);
            when(traceSource.countUnscored()).thenReturn(2L);
            when(traceSource.fetchUnscored(2)).thenReturn(List.of(sample("t1", "回答一"), sample("t2", "回答二")));
            when(metricRegistry.run(any(EvalSample.class), any(MetricContext.class), anyList()))
                    .thenAnswer(invocation -> {
                        // 真指标会调判定模型，这里在 Answer 里调一次，好把 token 与 traceId 一起验掉
                        MetricContext context = invocation.getArgument(1);
                        context.judge().judge(null, "判定提示");
                        return Map.of("faithfulness", score(0.8),
                                // 没标注的线上样本里，要求 ground truth 的指标自己跳过：不出分也就不进均值
                                "contextRecall", MetricScore.skipped("没有标注"));
                    });
            when(modelClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                    .content("{\"score\":0.8}").promptTokens(30).completionTokens(70).build());
            when(traceSource.markScored(eq("t1"), any())).thenReturn(true);
            when(traceSource.markScored(eq("t2"), any())).thenReturn(true);

            OnlineEvalSummaryDTO summary = service.score(request(2, null, null));

            assertThat(summary.getSampled()).isEqualTo(2);
            assertThat(summary.getScored()).isEqualTo(2);
            assertThat(summary.getWithoutScore()).isZero();
            assertThat(summary.getMetrics()).containsOnlyKeys("faithfulness");
            assertThat(summary.getMetrics().get("faithfulness").avg()).isEqualTo(0.8);
            assertThat(summary.getMetrics().get("faithfulness").cases()).isEqualTo(2);
            assertThat(summary.getJudgeTokens()).isEqualTo(200L);

            ArgumentCaptor<Map<String, Double>> scoresCap = ArgumentCaptor.forClass(Map.class);
            verify(traceSource).markScored(eq("t1"), scoresCap.capture());
            assertThat(scoresCap.getValue()).containsExactly(Map.entry("faithfulness", 0.8));
        }

        @Test
        void shouldTagJudgeCallsWithEvalPrefixAndDefaultSystemPrompt() {
            stubJudgeModel();
            stubMetrics(1);
            when(traceSource.countUnscored()).thenReturn(1L);
            when(traceSource.fetchUnscored(1)).thenReturn(List.of(sample("t1", "回答")));
            when(metricRegistry.run(any(EvalSample.class), any(MetricContext.class), anyList()))
                    .thenAnswer(invocation -> {
                        MetricContext context = invocation.getArgument(1);
                        // 指标没自报系统提示时按默认口径补上
                        context.judge().judge("  ", "判定提示");
                        return Map.of("faithfulness", score(0.5));
                    });
            when(modelClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                    .content("{}").promptTokens(1).completionTokens(2).build());
            when(traceSource.markScored(anyString(), any())).thenReturn(true);

            service.score(request(1, null, null));

            ArgumentCaptor<ChatRequest> requestCap = ArgumentCaptor.forClass(ChatRequest.class);
            verify(modelClient).chat(requestCap.capture());
            ChatRequest judgeRequest = requestCap.getValue();
            // 判定调用自己也会落 llm_trace：不给它可识别的归属，下一轮抽样就会把判定输出当线上问答再评一遍
            assertThat(judgeRequest.getTraceContext().getTraceId()).startsWith("eval-online-");
            assertThat(judgeRequest.getTraceContext().getModelId()).isEqualTo("m-default");
            assertThat(judgeRequest.getMessages().get(0).getContent())
                    .contains("你是严格的评测器");
            assertThat(judgeRequest.getTemperature()).isZero();
        }

        @Test
        void shouldMarkUnscorableSamplesWithoutRunningMetrics() {
            stubJudgeModel();
            stubMetrics(1);
            when(traceSource.countUnscored()).thenReturn(2L);
            // 正文没落库（关了存储或被截断）：评也评不出东西，但仍要标已评，不然积压推不动
            when(traceSource.fetchUnscored(2)).thenReturn(List.of(
                    sample("t1", "  "), sample("t2", "回答")));
            when(traceSource.markScored(anyString(), any())).thenReturn(true);
            when(metricRegistry.run(any(EvalSample.class), any(MetricContext.class), anyList()))
                    .thenReturn(Map.of("faithfulness", score(0.6)));

            OnlineEvalSummaryDTO summary = service.score(request(2, null, null));

            ArgumentCaptor<Map<String, Double>> emptyCap = ArgumentCaptor.forClass(Map.class);
            verify(traceSource).markScored(eq("t1"), emptyCap.capture());
            assertThat(emptyCap.getValue()).isEmpty();
            verify(metricRegistry, times(1)).run(any(), any(), any());
            assertThat(summary.getWithoutScore()).isEqualTo(1);
            assertThat(summary.getScored()).isEqualTo(1);
        }

        @Test
        void shouldKeepLostRaceOutOfTheAverage() {
            stubJudgeModel();
            stubMetrics(1);
            when(traceSource.countUnscored()).thenReturn(2L);
            when(traceSource.fetchUnscored(2)).thenReturn(List.of(
                    sample("t1", "回答一"), sample("t2", "回答二")));
            when(metricRegistry.run(any(EvalSample.class), any(MetricContext.class), anyList()))
                    .thenReturn(Map.of("faithfulness", score(0.4)));
            // 定时任务与手动触发撞上：被抢先标注的那条本轮分数不入库、也不计入均值
            when(traceSource.markScored(eq("t1"), any())).thenReturn(true);
            when(traceSource.markScored(eq("t2"), any())).thenReturn(false);

            OnlineEvalSummaryDTO summary = service.score(request(2, null, null));

            assertThat(summary.getScored()).isEqualTo(1);
            assertThat(summary.getLostRace()).isEqualTo(1);
            assertThat(summary.getMetrics().get("faithfulness").cases()).isEqualTo(1);
            assertThat(summary.getMetrics().get("faithfulness").avg()).isEqualTo(0.4);
        }

        @Test
        void shouldResolveJudgeBeforeTouchingAnySample() {
            // 判定模型解析失败要在开跑前炸：等到第一条样本才炸的话，剩下的样本会被逐条标成
            // 「评过但没分」，一批没评的样本就此被配置错误消耗掉
            when(modelService.getDefaultModel()).thenReturn(null);
            when(traceSource.countUnscored()).thenReturn(3L);
            when(traceSource.fetchUnscored(3)).thenReturn(List.of(sample("t1", "回答")));
            stubMetrics(1);

            assertThatThrownBy(() -> service.score(request(3, null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("无法在线评测");

            verify(traceSource, never()).markScored(anyString(), any());
            verify(metricRegistry, never()).run(any(), any(), anyList());
        }

        @Test
        void shouldUseExplicitJudgeModel() {
            when(modelService.getClient("m-given")).thenReturn(modelClient);
            stubMetrics(1);
            when(traceSource.countUnscored()).thenReturn(1L);
            when(traceSource.fetchUnscored(1)).thenReturn(List.of(sample("t1", "回答")));
            when(metricRegistry.run(any(EvalSample.class), any(MetricContext.class), anyList()))
                    .thenReturn(Map.of("faithfulness", score(1.0)));
            when(traceSource.markScored(anyString(), any())).thenReturn(true);

            service.score(request(1, "m-given", null));

            // 指定了判定模型就不该去碰默认模型
            verify(modelService, never()).getDefaultModel();
            verify(modelService).getClient("m-given");
        }
    }

    @Nested
    class Scheduled {

        @Test
        void shouldStayQuietWhenDisabled() {
            // 定时抽样默认关：开着它就等于默认同意持续花钱，必须由部署方显式打开
            ReflectionTestUtils.setField(service, "scheduleEnabled", false);

            service.scheduledScore();

            verifyNoInteractions(traceSource, metricRegistry);
        }

        @Test
        void shouldSwallowFailureAndKeepTheNextRound() {
            // 定时任务里抛出去只会终止后续调度：这一轮按失败记录，下一轮照常
            ReflectionTestUtils.setField(service, "scheduleEnabled", true);
            ReflectionTestUtils.setField(service, "scheduledLimit", 7);
            when(traceSource.countUnscored()).thenThrow(new IllegalStateException("库没连上"));

            service.scheduledScore();

            verify(traceSource).countUnscored();
        }

        @Test
        void shouldScoreScheduledLimitWhenEnabled() {
            ReflectionTestUtils.setField(service, "scheduleEnabled", true);
            ReflectionTestUtils.setField(service, "scheduledLimit", 7);
            when(traceSource.countUnscored()).thenReturn(0L);
            when(traceSource.fetchUnscored(7)).thenReturn(List.of());
            when(metricRegistry.resolve(any())).thenReturn(List.of());

            service.scheduledScore();

            verify(traceSource).fetchUnscored(7);
        }
    }

    private static OnlineEvalRequestDTO request(Integer limit, String judgeModelId, List<String> metrics) {
        OnlineEvalRequestDTO request = new OnlineEvalRequestDTO();
        request.setLimit(limit);
        request.setJudgeModelId(judgeModelId);
        request.setMetrics(metrics);
        return request;
    }
}
