package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.LlmTraceStatsDTO;
import cn.cangjiecloud.observability.dto.OnlineEvalTrendDTO;
import cn.cangjiecloud.observability.mapper.ObservabilityStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmTraceStatsServiceImpl} 单元测试。
 * <p>
 * 覆盖三块容易写错的地方：窗口口径（自然日 + 越界夹取）、聚合结果的类型收口（缺列 / 脏值
 * 不能让面板 500），以及在线评测趋势的按天分桶（脏 JSON、区间外分数、认不出日期的行
 * 各自该落在哪里）。Mapper 全 mock，不连库。
 */
@ExtendWith(MockitoExtension.class)
class LlmTraceStatsServiceImplTest {

    @Mock
    private ObservabilityStatsMapper statsMapper;

    private LlmTraceStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmTraceStatsServiceImpl(statsMapper);
    }

    /** 捕获传给 Mapper 的窗口起点 */
    private LocalDateTime captureSummaryStart() {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsMapper).llmTraceSummary(captor.capture());
        return captor.getValue();
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            row.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return row;
    }

    @Nested
    class WindowStart {

        @Test
        void shouldStartAtMidnightOfNaturalDays() {
            // 「近 7 天」是 7 个完整的格子：起点必须是 6 天前的 0 点，而不是 now 减 7*24 小时
            service.stats(7);

            assertThat(captureSummaryStart())
                    .isEqualTo(LocalDate.now().minusDays(6).atStartOfDay());
        }

        @Test
        void shouldClampDaysIntoRange() {
            // 非正数按 1 天：窗口为 0 或负数会把「今天的格子」整个丢掉
            service.stats(0);
            service.stats(-5);
            // 上限 365：再大的窗口在这张表上没有索引能救
            service.stats(1000);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(statsMapper, times(3)).llmTraceSummary(captor.capture());

            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            assertThat(captor.getAllValues()).containsExactly(
                    todayStart, todayStart, LocalDate.now().minusDays(364).atStartOfDay());
        }

        @Test
        void trendShouldUseTheSameWindow() {
            service.onlineEvalTrend(3);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(statsMapper).llmTraceEvalCoverage(captor.capture());
            assertThat(captor.getValue()).isEqualTo(LocalDate.now().minusDays(2).atStartOfDay());
        }
    }

    @Nested
    class StatsMapping {

        @Test
        void shouldMapAggregatesAndDeriveErrorRate() {
            when(statsMapper.llmTraceSummary(any())).thenReturn(row(
                    "calls", 120L, "failures", 6L, "cost", new BigDecimal("1.2345"),
                    "tokens", 4200L, "avg_duration", 812.5, "p50_duration", 700.0,
                    "p95_duration", 2100.0));
            when(statsMapper.llmTraceTtftSummary(any())).thenReturn(row(
                    "ttft_calls", 40L, "avg_ttft", 320.0, "p95_ttft", 900.0));
            when(statsMapper.llmTraceByModel(any(), eq(20))).thenReturn(List.of(
                    row("model_name", "gpt-4o", "calls", 100L, "failures", 5L,
                            "cost", new BigDecimal("1.0"), "tokens", 4000L, "p95_duration", 2000.0),
                    // 没名字的模型（配置被删过）也得有一行，不能整条丢掉
                    row("calls", 0L, "failures", 0L)));

            LlmTraceStatsDTO stats = service.stats(7);

            assertThat(stats.getCalls()).isEqualTo(120L);
            assertThat(stats.getFailures()).isEqualTo(6L);
            // 错误率是个比例，不是百分数：6/120 = 0.05
            assertThat(stats.getErrorRate()).isEqualByComparingTo("0.0500");
            assertThat(stats.getCost()).isEqualByComparingTo("1.2345");
            assertThat(stats.getTokens()).isEqualTo(4200L);
            assertThat(stats.getP95Duration()).isEqualTo(2100.0);
            assertThat(stats.getStreamingCalls()).isEqualTo(40L);
            assertThat(stats.getP95Ttft()).isEqualTo(900.0);

            assertThat(stats.getByModel()).hasSize(2);
            assertThat(stats.getByModel().get(0).getModelName()).isEqualTo("gpt-4o");
            assertThat(stats.getByModel().get(0).getErrorRate()).isEqualByComparingTo("0.0500");
            assertThat(stats.getByModel().get(1).getModelName()).isEqualTo("未知模型");
            assertThat(stats.getByModel().get(1).getCalls()).isZero();
        }

        @Test
        void shouldTreatMissingAggregatesAsZero() {
            // 窗口内一条调用都没有时三个查询都可能返回 null：接口要给出一个全 0 的读数，
            // 而不是让面板去区分「没有数据」和「接口挂了」
            LlmTraceStatsDTO stats = service.stats(1);

            assertThat(stats.getCalls()).isZero();
            assertThat(stats.getErrorRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(stats.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(stats.getP95Duration()).isNull();
            assertThat(stats.getByModel()).isEmpty();
        }

        @Test
        void shouldIgnoreNonNumericValues() {
            // 聚合列被人换成文本、或驱动返回了意料之外的类型时按「没有数」收口：
            // 一个面板接口不该因为一列类型对不上而 500
            when(statsMapper.llmTraceSummary(any())).thenReturn(row(
                    "calls", "abc", "tokens", Boolean.TRUE, "cost", "[1]"));

            LlmTraceStatsDTO stats = service.stats(7);

            assertThat(stats.getCalls()).isZero();
            assertThat(stats.getTokens()).isZero();
            assertThat(stats.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
            // 失败数取不到就按 0：错误率是 0 而不是把整块面板打挂
            assertThat(stats.getErrorRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class OnlineEvalTrendMapping {

        @Test
        void shouldBucketScoresByDayAndSkipDirtyRows() {
            when(statsMapper.llmTraceEvalCoverage(any())).thenReturn(List.of(
                    row("day", "2026-10-11", "scored", 5L, "pending", 4L),
                    // 同一天的多行要合并：窗口按小时切过、group by 只可能一行，但合并写法更省事
                    row("day", "2026-10-10", "scored", 3L, "pending", 1L)));
            when(statsMapper.llmTraceScoredScores(any(), eq(5000))).thenReturn(List.of(
                    row("start_time", LocalDateTime.of(2026, 10, 11, 9, 0),
                            // 区间外的分数不进门：有人把它改成 100 分制时不该带走整个均值
                            "eval_scores", "{\"faithfulness\":0.8,\"contextPrecision\":1.5}"),
                    row("start_time", LocalDateTime.of(2026, 10, 10, 9, 0),
                            "eval_scores", "{\"faithfulness\":0.6}"),
                    row("start_time", LocalDateTime.of(2026, 10, 10, 10, 0), "eval_scores", "not-json"),
                    // 认不出属于哪一天：只进窗口总量，不落进某一天的格子
                    row("start_time", null, "eval_scores", "{\"faithfulness\":1.0}")));

            OnlineEvalTrendDTO trend = service.onlineEvalTrend(7);

            assertThat(trend.getScored()).isEqualTo(8L);
            assertThat(trend.getPending()).isEqualTo(5L);
            // 覆盖率 = 8 / 13，保留 4 位
            assertThat(trend.getCoverage()).isCloseTo(0.6154, offset(1e-9));

            // 0.8 / 0.6 / 1.0 三条进均值，contextPrecision 那条越界的不算
            assertThat(trend.getMetrics()).containsOnlyKeys("faithfulness");
            assertThat(trend.getMetrics().get("faithfulness").getAvg()).isEqualTo(0.8);
            assertThat(trend.getMetrics().get("faithfulness").getCases()).isEqualTo(3);

            // 日期升序，脏 JSON 那一行不产生格子
            assertThat(trend.getDaily()).extracting(OnlineEvalTrendDTO.Daily::getDay)
                    .containsExactly("2026-10-10", "2026-10-11");
            assertThat(trend.getDaily().get(0).getScored()).isEqualTo(3L);
            assertThat(trend.getDaily().get(0).getMetrics().get("faithfulness").getCases()).isEqualTo(1);
            assertThat(trend.getDaily().get(1).getPending()).isEqualTo(4L);
            assertThat(trend.getDaily().get(1).getMetrics().get("faithfulness").getAvg()).isEqualTo(0.8);
        }

        @Test
        void shouldReportZeroCoverageWhenNothingEvaluated() {
            // 一条都没评时覆盖率是 0 而不是 NaN：分母为 0 的除法最容易被写成 null
            when(statsMapper.llmTraceEvalCoverage(any())).thenReturn(List.of(
                    row("day", "2026-10-10", "scored", 0L, "pending", 7L)));
            when(statsMapper.llmTraceScoredScores(any(), anyInt())).thenReturn(List.of());

            OnlineEvalTrendDTO trend = service.onlineEvalTrend(7);

            assertThat(trend.getScored()).isZero();
            assertThat(trend.getPending()).isEqualTo(7L);
            assertThat(trend.getCoverage()).isZero();
            assertThat(trend.getMetrics()).isEmpty();
            assertThat(trend.getDaily()).hasSize(1);
            assertThat(trend.getDaily().get(0).getMetrics()).isEmpty();
        }

        @Test
        void shouldRoundCoverageToFourDigits() {
            when(statsMapper.llmTraceEvalCoverage(any())).thenReturn(List.of(
                    row("day", "2026-10-10", "scored", 1L, "pending", 2L)));

            // 1/3 = 0.3333...，面板上没人要六位小数
            assertThat(service.onlineEvalTrend(7).getCoverage()).isEqualTo(0.3333);
        }
    }
}
