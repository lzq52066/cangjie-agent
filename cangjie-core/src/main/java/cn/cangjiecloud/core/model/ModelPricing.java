package cn.cangjiecloud.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一个模型的 token 单价（元 / 千 token）。
 * <p>
 * 两个方向分开记：主流厂商的输出单价通常是输入单价的数倍，合成一个价会把成本算错一大截，
 * 而「推理模型的输入更贵」这类差异也只能靠两列表达。
 * <p>
 * 单价为 null 表示「没维护这个价」，此时成本算不出来而不是算成 0：
 * 把没定价记成免费，看板上的总成本会静默偏低，而且看不出哪里漏了。
 *
 * @param inputPrice  输入单价（元 / 千 token），可为 null
 * @param outputPrice 输出单价（元 / 千 token），可为 null
 */
public record ModelPricing(BigDecimal inputPrice, BigDecimal outputPrice) {

    /** 千 token 计价换算用的分母 */
    private static final BigDecimal PER_THOUSAND = BigDecimal.valueOf(1000);

    /** 成本保留位数：与 llm_trace.cost 的 numeric(12,6) 对齐 */
    private static final int COST_SCALE = 6;

    public boolean hasPrice() {
        return inputPrice != null || outputPrice != null;
    }

    /**
     * 估算一次调用的成本。
     * <p>
     * 只给了单边单价时按单边估算（另一边视为未定价），两个都没给返回 null ——
     * 宁可让这一格空着，也不要在报表里造一个看起来合理的 0。
     */
    public BigDecimal costOf(Long inputTokens, Long outputTokens) {
        if (!hasPrice()) {
            return null;
        }
        BigDecimal cost = BigDecimal.ZERO;
        boolean counted = false;
        if (inputPrice != null && inputTokens != null && inputTokens > 0) {
            cost = cost.add(inputPrice.multiply(BigDecimal.valueOf(inputTokens).divide(PER_THOUSAND)));
            counted = true;
        }
        if (outputPrice != null && outputTokens != null && outputTokens > 0) {
            cost = cost.add(outputPrice.multiply(BigDecimal.valueOf(outputTokens).divide(PER_THOUSAND)));
            counted = true;
        }
        return counted ? cost.setScale(COST_SCALE, RoundingMode.HALF_UP) : null;
    }
}
