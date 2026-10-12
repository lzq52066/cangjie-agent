package cn.cangjiecloud.eval.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 一轮在线评测的结果（执行摘要，不是分数曲线）。
 * <p>
 * 分数本身就地写在每条调用记录上，这里只回答「这一轮评了多少条、评出什么水位、
 * 有多少条压根没评出来」—— 最后那一项最容易被漏，而它恰恰决定了均值能不能信。
 */
@Data
@Builder
public class OnlineEvalSummaryDTO {

    /** 本轮开始前还有多少条待评（积压水位） */
    private long pendingBefore;

    /** 本轮取到并送进指标的样本数 */
    private int sampled;

    /** 成功回填分数的条数 */
    private int scored;

    /** 一个指标分数都没出的条数（没片段 / 没回答 / 判定全挂）：仍按已评落库，避免反复重扫 */
    private int withoutScore;

    /** 被其它执行抢先标注、本轮写空了的条数（定时任务与手动触发撞车的证据） */
    private int lostRace;

    /** 本轮判定调用消耗的 token */
    private long judgeTokens;

    /** 本轮均值：指标名 → {avg, cases}，cases 是出了该指标分数的样本数 */
    private Map<String, MetricAvg> metrics;

    /**
     * 单个指标的线上均值。
     *
     * @param avg   0~1 平均分（保留 4 位）
     * @param cases 出了这个指标分数的样本数：分母不是样本总数，2 条与 200 条的均值可信度完全不同
     */
    public record MetricAvg(Double avg, int cases) {
    }
}
