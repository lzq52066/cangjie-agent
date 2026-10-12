package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.observability.dto.LlmTraceStatsDTO;
import cn.cangjiecloud.observability.dto.OnlineEvalTrendDTO;
import cn.cangjiecloud.observability.mapper.ObservabilityStatsMapper;
import cn.cangjiecloud.observability.service.ILlmTraceStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 模型调用统计实现：三次窗口聚合（汇总 / 首字延迟 / 按模型）拼成面板要的那一块，
 * 另加一份在线评测的覆盖度与均值趋势。
 * <p>
 * 聚合尽量在 SQL 里做，Java 侧只做类型收口 —— 把百万行拉回内存排序是最容易写出来、
 * 也最容易把一个面板接口拖死的做法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTraceStatsServiceImpl implements ILlmTraceStatsService {

    /**
     * 按模型切片最多展示几个模型：模型数理论上无上限，面板只关心成本最高的那几个，
     * 剩下的都是长尾，全量返回只会让接口体积跟着模型数涨。
     */
    private static final int MAX_MODEL_ROWS = 20;

    /** 窗口上限（天）：跨年的窗口在这张表上没有索引能救，宁可让调用方分页看。 */
    private static final int MAX_WINDOW_DAYS = 365;

    /**
     * 趋势一次最多读多少条已评样本。
     * <p>
     * 指标名是动态的，均值只能在 Java 侧汇总；行数是唯一能把这件事控住的东西。
     * 真到需要更多样本的规模，应该换成按指标建列的聚合而不是把这一页抹大。
     */
    private static final int MAX_TREND_ROWS = 5000;

    private final ObservabilityStatsMapper statsMapper;

    @Override
    public LlmTraceStatsDTO stats(int days) {
        LocalDateTime start = windowStart(days);

        Map<String, Object> summary = orEmpty(statsMapper.llmTraceSummary(start));
        Map<String, Object> ttft = orEmpty(statsMapper.llmTraceTtftSummary(start));
        List<Map<String, Object>> modelRows = statsMapper.llmTraceByModel(start, MAX_MODEL_ROWS);

        long calls = longOf(summary.get("calls"));
        long failures = longOf(summary.get("failures"));

        List<LlmTraceStatsDTO.ModelStat> byModel = new ArrayList<>();
        if (modelRows != null) {
            for (Map<String, Object> row : modelRows) {
                long rowCalls = longOf(row.get("calls"));
                byModel.add(LlmTraceStatsDTO.ModelStat.builder()
                        .modelName(String.valueOf(row.getOrDefault("model_name", "未知模型")))
                        .calls(rowCalls)
                        .failures(longOf(row.get("failures")))
                        .errorRate(errorRate(longOf(row.get("failures")), rowCalls))
                        .cost(decimalOf(row.get("cost")))
                        .tokens(longOf(row.get("tokens")))
                        .p95Duration(doubleOf(row.get("p95_duration")))
                        .build());
            }
        }

        return LlmTraceStatsDTO.builder()
                .calls(calls)
                .failures(failures)
                .errorRate(errorRate(failures, calls))
                .cost(decimalOf(summary.get("cost")))
                .tokens(longOf(summary.get("tokens")))
                .avgDuration(doubleOf(summary.get("avg_duration")))
                .p50Duration(doubleOf(summary.get("p50_duration")))
                .p95Duration(doubleOf(summary.get("p95_duration")))
                .streamingCalls(longOf(ttft.get("ttft_calls")))
                .avgTtft(doubleOf(ttft.get("avg_ttft")))
                .p95Ttft(doubleOf(ttft.get("p95_ttft")))
                .byModel(byModel)
                .build();
    }

    /**
     * 窗口起点：最近 N 个自然日的 0 点（含今天），days 越界就近夹到 [1, 365]。
     * <p>
     * 按自然日而不是「now - N 天」：面板上写「近 7 天」时，运营看的是 7 个完整的格子，
     * 而不是今天只剩一半、上周被整段丢掉。
     */
    private static LocalDateTime windowStart(int days) {
        int window = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        return LocalDate.now().minusDays(window - 1L).atStartOfDay();
    }

    /**
     * 错误率：无调用时是 0 而不是 null，也不是 NaN —— 面板要能直接乘 100 成一个百分数。
     */
    private static BigDecimal errorRate(long failures, long calls) {
        if (calls <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(failures).divide(BigDecimal.valueOf(calls), 4, RoundingMode.HALF_UP);
    }

    @Override
    public OnlineEvalTrendDTO onlineEvalTrend(int days) {
        LocalDateTime start = windowStart(days);
        List<Map<String, Object>> coverage = statsMapper.llmTraceEvalCoverage(start);
        List<Map<String, Object>> scoredRows = statsMapper.llmTraceScoredScores(start, MAX_TREND_ROWS);

        // 日期形如 yyyy-MM-dd，字典序就是时间序，直接用 TreeMap 排序而不必引入日期解析
        Map<String, long[]> counts = new TreeMap<>();
        if (coverage != null) {
            for (Map<String, Object> row : coverage) {
                String day = String.valueOf(row.getOrDefault("day", ""));
                long[] slot = counts.computeIfAbsent(day, key -> new long[2]);
                slot[0] += longOf(row.get("scored"));
                slot[1] += longOf(row.get("pending"));
            }
        }

        Map<String, double[]> overall = new LinkedHashMap<>();
        Map<String, Map<String, double[]>> byDay = new LinkedHashMap<>();
        if (scoredRows != null) {
            for (Map<String, Object> row : scoredRows) {
                Map<String, Object> scores = parseScores((String) row.get("eval_scores"));
                if (scores.isEmpty()) {
                    continue;
                }
                String day = dayOf(row.get("start_time"));
                // 认不出属于哪一天的行只进窗口总量，不落进某一天的格子里
                Map<String, double[]> dayAggregates = day == null
                        ? new LinkedHashMap<>() : byDay.computeIfAbsent(day, key -> new LinkedHashMap<>());
                scores.forEach((name, value) -> {
                    Double score = asScore(value);
                    if (score != null) {
                        accumulate(overall, name, score);
                        accumulate(dayAggregates, name, score);
                    }
                });
            }
        }

        long scoredTotal = 0L;
        long pendingTotal = 0L;
        for (long[] slot : counts.values()) {
            scoredTotal += slot[0];
            pendingTotal += slot[1];
        }
        Set<String> allDays = new TreeSet<>(counts.keySet());
        allDays.addAll(byDay.keySet());
        List<OnlineEvalTrendDTO.Daily> daily = new ArrayList<>(allDays.size());
        for (String day : allDays) {
            long[] slot = counts.getOrDefault(day, new long[2]);
            daily.add(OnlineEvalTrendDTO.Daily.builder()
                    .day(day)
                    .scored(slot[0])
                    .pending(slot[1])
                    .metrics(toAverages(byDay.get(day)))
                    .build());
        }

        return OnlineEvalTrendDTO.builder()
                .scored(scoredTotal)
                .pending(pendingTotal)
                .coverage(scoredTotal + pendingTotal == 0 ? 0.0
                        : Math.round(scoredTotal * 10000.0 / (scoredTotal + pendingTotal)) / 10000.0)
                .metrics(toAverages(overall))
                .daily(daily)
                .build();
    }

    private static void accumulate(Map<String, double[]> target, String name, double value) {
        double[] slot = target.computeIfAbsent(name, key -> new double[2]);
        slot[0] += value;
        slot[1]++;
    }

    /**
     * 累加器 → 均值表。cases 跟均值一起给：只评了 2 条的指标与评了 200 条的指标在图上长得一样，
     * 不标样本数就是让读图的人自己去猜分母。
     * <p>
     * 入参允许为 null：只有待评、一条没评的那天就没有累加器，面板要的是一个空格子而不是 500。
     */
    private static Map<String, OnlineEvalTrendDTO.MetricAvg> toAverages(Map<String, double[]> aggregates) {
        Map<String, OnlineEvalTrendDTO.MetricAvg> averages = new LinkedHashMap<>();
        if (aggregates == null) {
            return averages;
        }
        aggregates.forEach((name, slot) -> {
            if (slot[1] > 0) {
                averages.put(name, OnlineEvalTrendDTO.MetricAvg.builder()
                        .avg(Math.round(slot[0] / slot[1] * 10000.0) / 10000.0)
                        .cases((int) slot[1])
                        .build());
            }
        });
        return averages;
    }

    /** 分数列里被人写进去的脏数据也解得开：解析失败按「这条没分」处理，不能让一个面板接口 500 */
    private static Map<String, Object> parseScores(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseMap(json);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.debug("在线评测分数解析失败，跳过这一条: {}", e.toString());
            return Map.of();
        }
    }

    /** 只认 [0,1] 的数：区间外（或被谁手动改成了 100 分制）不进门，免得一条脏数据把均值带走 */
    private static Double asScore(Object value) {
        if (value instanceof Number number) {
            double score = number.doubleValue();
            return score >= 0.0 && score <= 1.0 ? score : null;
        }
        return null;
    }

    /**
     * 行时间 → 天。取不到日期时返回 null：这一条仍计入窗口总量，只是不知道它属于哪天。
     */
    private static String dayOf(Object value) {
        LocalDateTime time = null;
        if (value instanceof LocalDateTime localDateTime) {
            time = localDateTime;
        } else if (value instanceof Timestamp timestamp) {
            time = timestamp.toLocalDateTime();
        }
        return time == null ? null : String.format("%04d-%02d-%02d",
                time.getYear(), time.getMonthValue(), time.getDayOfMonth());
    }

    private static Map<String, Object> orEmpty(Map<String, Object> map) {
        return map == null ? Collections.emptyMap() : map;
    }

    private static long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    /**
     * 金额按 BigDecimal 收口：这里走的是聚合结果，用 double 接会把累加误差摆到面板上。
     */
    private static BigDecimal decimalOf(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }
}
