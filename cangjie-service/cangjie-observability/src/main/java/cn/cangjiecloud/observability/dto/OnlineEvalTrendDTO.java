package cn.cangjiecloud.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 在线评测的水位与趋势（按天）。
 * <p>
 * 与 {@link LlmTraceStatsDTO} 分开是口径不同：那一份看「调用本身快不快、贵不贵」，
 * 这一份看「回答质量怎么样、评了多少、还有多少没评」。积压那一项尤其不能省 ——
 * 均值再好看，只评了 3% 的样本就等于没有监控。
 */
@Data
@Builder
public class OnlineEvalTrendDTO {

    /** 窗口内已评条数 */
    private long scored;

    /** 窗口内待评条数（积压） */
    private long pending;

    /** 评测覆盖率（0~1，保留 4 位）：没有调用时为 0 */
    private Double coverage;

    /** 窗口内各指标均值 */
    private Map<String, MetricAvg> metrics;

    /** 按天序列，日期升序 */
    private List<Daily> daily;

    /**
     * 一个指标在窗口内的均值。
     * <p>
     * cases 是出了这个指标分数的条数，不是已评条数：光看均值会把「大半样本根本没这个指标」
     * 看成全体水位。
     */
    @Data
    @Builder
    public static class MetricAvg {
        private Double avg;
        private int cases;
    }

    /**
     * 一天的在线评测读数：day 是日期（yyyy-MM-dd），scored / pending 是当天的已评与待评条数。
     */
    @Data
    @Builder
    public static class Daily {
        private String day;
        private long scored;
        private long pending;
        private Map<String, MetricAvg> metrics;
    }
}
