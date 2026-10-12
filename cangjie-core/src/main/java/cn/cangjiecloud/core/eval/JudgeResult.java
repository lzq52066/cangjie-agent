package cn.cangjiecloud.core.eval;

/**
 * 一次判定调用的结果。
 * <p>
 * 带上 token 用量：评估的成本口径必须把「回答 + 各指标判定」全算进去，
 * 只统计回答那一路会让报告上的 Token 数比真实花费少一大截（多指标时尤其明显）。
 *
 * @param output           模型输出文本
 * @param inputTokens      输入 token（拿不到时传 0）
 * @param outputTokens     输出 token（拿不到时传 0）
 */
public record JudgeResult(String output, long inputTokens, long outputTokens) {

    public static JudgeResult of(String output) {
        return new JudgeResult(output, 0L, 0L);
    }
}
