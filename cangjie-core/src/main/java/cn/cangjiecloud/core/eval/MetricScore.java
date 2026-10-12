package cn.cangjiecloud.core.eval;

import java.util.Map;

/**
 * 指标得分。
 * <p>
 * {@code value} 为 null 有明确语义：「本次没评这个指标」，绝不能当 0 计入均值 ——
 * 把没评测显示成零分，会凭空拉低水位，据此调参只会调错方向。跳过原因放在
 * {@code detail} 里（约定 key {@code skipped}），报告里能直接看到「为什么没分」。
 *
 * @param value       0~1 分数，null 表示未评测
 * @param sampleCount 参与计算的样本数（如 faithfulness 的断言条数、precision 的片段数），
 *                    用于跨 case 汇总时判断这个均值的可信度
 * @param detail      中间量与跳过原因，可为 null
 */
public record MetricScore(Double value, int sampleCount, Map<String, Object> detail) {

    /** detail 里表示「为什么没出分」的约定 key */
    public static final String SKIP_REASON = "skipped";

    /** detail 里表示「该出分却没出」的约定 key */
    public static final String ERROR_REASON = "error";

    /**
     * 结构性不适用（没片段 / 没标注 / 回答无可核查内容）：不影响本条 case 的成败判定。
     * <p>
     * 未评测绝不能拿一个中性值（0.5）顶替 —— 均值反映的就不再是「答得怎么样」，
     * 而是「判分接口今天稳不稳」，据此调参只会调错方向。
     */
    public static MetricScore skipped(String reason) {
        return new MetricScore(null, 0, Map.of(SKIP_REASON, reason));
    }

    /**
     * 本该出分却没出（调用失败 / 输出无法对齐）：与 {@link #skipped} 区分开，
     * 报告要把这种「指标摸不到」的 case 标成部分，否则上游只看到均值下降却查不出原因。
     */
    public static MetricScore failed(String reason) {
        return new MetricScore(null, 0, Map.of(ERROR_REASON, reason));
    }

    public static MetricScore of(double value) {
        return new MetricScore(value, 1, null);
    }

    public static MetricScore of(double value, int sampleCount) {
        return new MetricScore(value, sampleCount, null);
    }

    public static MetricScore of(double value, int sampleCount, Map<String, Object> detail) {
        return new MetricScore(value, sampleCount, detail);
    }

    /** 是否真的出了分 */
    public boolean evaluated() {
        return value != null;
    }

    /** 分数取到 [0,1] 区间内的值，越界或为空返回 null（由调用方按「无效输出」处理） */
    public static Double clamp(Double raw) {
        if (raw == null || raw.isNaN() || raw < 0.0 || raw > 1.0) {
            return null;
        }
        return raw;
    }
}
