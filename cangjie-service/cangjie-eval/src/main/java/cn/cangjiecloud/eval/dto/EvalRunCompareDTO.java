package cn.cangjiecloud.eval.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 两次评估运行的对比结果。
 * <p>
 * 只看一份报告答不了「这次改动到底让水位涨了还是跌了」；而只比汇总均值也不够 ——
 * 均值涨了但十条关键用例跌了，是一次典型的、靠平均值藏住的回归。所以两边都给：
 * 汇总差值 + 逐用例差值，并按跌得最狠的排在前面。
 */
@Data
@Builder
public class EvalRunCompareDTO {

    /** 基准运行 ID（「改动前」） */
    private String fromRunId;

    /** 对比运行 ID（「改动后」） */
    private String toRunId;

    private String fromStatus;

    private String toStatus;

    private LocalDateTime fromStartTime;

    private LocalDateTime toStartTime;

    /**
     * 两次运行是否同一数据集。
     * <p>
     * 不同数据集时逐用例对比没有意义（用例集都不一样，均值差值里混的是题目差异），
     * 这个标记就是让前端能明确提示「口径不可比」，而不是把一个负数当成回归。
     */
    private boolean sameDataset;

    /** 汇总指标差值：指标名 → 差值项（含 recall / correctness 与各 SPI 指标） */
    private Map<String, MetricDelta> metrics;

    /** 运行开销差值（耗时 / token）：分数没跌但成本翻倍，同样是一次要看见的退化 */
    private Map<String, MetricDelta> cost;

    /** 逐用例差值，跌得最狠的在前 */
    private List<CaseDiff> cases;

    /** 综合分下跌的用例数 */
    private int regressed;

    /** 综合分上涨的用例数 */
    private int improved;

    /** 只出现在其中一侧的用例数（被删 / 新增 / 那条 case 明细写失败） */
    private int onlyInFrom;

    private int onlyInTo;

    /**
     * 回归阈值：指标均值比基准跌过这个幅度就告警（绝对分，0.05 即 5 个百分点）。
     * <p>
     * 跟着读数一起吐出来而不是让前端写死：调阈值这件事本身就该在对比结果里看得见，
     * 否则同一份差值在不同配置下会被读成「通过」和「拦下」两种结论。
     */
    private double regressThreshold;

    /** 跌过阈值的指标，空即本次对比没有需要拦下来的退化 */
    private List<RegressAlert> regressAlerts;

    /**
     * 一个指标的两次读数。
     * <ul>
     *   <li>{@code from / to}：某一侧的读数，没评这个指标时为空。</li>
     *   <li>{@code delta}：{@code to - from}；任一侧为空时为空 —— 不猜：把「没分」当成 0 参与差值，
     *       算出来的数只会把「没评测」显示成「跌到零」。</li>
     *   <li>{@code fromCases / toCases}：两侧各有多少条用例出了这个指标的分数。2 条与 200 条的
     *       均值不可比，光看均值会误判。</li>
     * </ul>
     */
    @Data
    @Builder
    public static class MetricDelta {
        private Double from;
        private Double to;
        private Double delta;
        private Integer fromCases;
        private Integer toCases;
    }

    /**
     * 一条回归告警：某个汇总指标均值跌过阈值。
     * <p>
     * 只按汇总均值判，不按单条 case 判 —— 单条用例跌穿阈值是常事（题目本身敏感），
     * 拿它当门禁会把每一次正常的样本抖动都拦成一次事故。
     */
    @Data
    @Builder
    public static class RegressAlert {
        /** 指标名（recall / correctness / faithfulness ...） */
        private String name;
        private Double from;
        private Double to;
        private Double delta;
        /** 对比侧出了多少条这个指标的分数：样本太少的均值不进告警 */
        private Integer toCases;
    }

    /**
     * 一条用例在两次运行里的差值。
     * <p>
     * 综合分是该 case 所有出了分的指标的均值；它会把「是哪一项跌的」掩盖掉，
     * 所以逐项差值一并给出 —— 定位永远回到这一层。
     */
    @Data
    @Builder
    public static class CaseDiff {
        private String caseId;
        private String question;
        private String fromStatus;
        private String toStatus;
        private Double compositeFrom;
        private Double compositeTo;
        private Double delta;
        private Map<String, MetricDelta> metrics;
    }
}
