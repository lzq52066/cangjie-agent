package cn.cangjiecloud.core.eval;

/**
 * 指标用的模型调用端口。
 * <p>
 * 放在 core 而不是让各指标直接依赖 {@code OpenAICompatibleClient}，是为了守住 SPI 中性：
 * 在线评测、后端任务、单元测试都能拿一个 lambda 顶进来，不必为此把模型模块拖进依赖树。
 * <p>
 * 调用失败请直接把异常抛出来，由 {@link EvalMetric#score} 的调用方按「本指标没出分」收敛，
 * 不要在内层悄悄吞掉后返回一个看似正常的空串 —— 那会让指标输出解析失败，
 * 最终落成一个来路不明的 0 分。
 */
@FunctionalInterface
public interface JudgeFunction {

    /**
     * 发起一次判定调用。
     *
     * @param system 系统提示（可为 null，表示由实现方决定默认值）
     * @param prompt 用户提示
     * @return 模型输出与 token 用量
     */
    JudgeResult judge(String system, String prompt);
}
