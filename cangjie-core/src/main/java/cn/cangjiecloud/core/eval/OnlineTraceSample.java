package cn.cangjiecloud.core.eval;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 一条待打分的线上调用样本（在线评测的输入）。
 * <p>
 * 与离线 {@link EvalSample} 的区别只在来源：这一条是从 {@code llm_trace} + 链路 span 里
 * 还原出来的真实对话，因此没有期望回答、也没有 ground truth —— 能算的指标由各自的
 * {@code supports} 判定，缺了就不出分，而不是补一个假分数。
 *
 * @param id         llm_trace 主键，回填分数靠它定位
 * @param traceId    链路 ID（跳回链路视图看证据）
 * @param sessionId  会话 ID
 * @param appId      应用 ID
 * @param modelName  模型名（按模型切片看线上水位）
 * @param query      用户问题（检索 span 的 query 优先，取不到时从 prompt 末条用户消息还原）
 * @param answer     线上真实回答，未存正文时为空
 * @param contexts   本次召回的片段，按名次排序；没开载荷存储时为空
 * @param occurredAt 调用发生时间
 */
public record OnlineTraceSample(String id,
                                String traceId,
                                String sessionId,
                                String appId,
                                String modelName,
                                String query,
                                String answer,
                                List<ContextChunk> contexts,
                                LocalDateTime occurredAt) {

    public OnlineTraceSample {
        contexts = contexts == null ? List.of()
                : contexts.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 交给指标计算的同构输入。
     * <p>
     * 线上样本压根没有标注，期望回答与 ground truth 一律留空：让指标自己按「缺什么不评什么」
     * 处理，比在这里伪造一个值要诚实。
     */
    public EvalSample toEvalSample() {
        return new EvalSample(query, answer, contexts, List.of(), List.of(), null);
    }

    /** 有没有可评的内容：没回答的样本送去打分只会得到一个无意义的 skip */
    public boolean scoreable() {
        return answer != null && !answer.isBlank();
    }
}
