package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 循环策略：轮次、超时、并发等约束
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoopPolicy {

    /** Function Calling 最大轮次 */
    @Builder.Default
    private int maxRounds = 5;

    /** 单次 run 的总超时（秒） */
    @Builder.Default
    private long timeoutSeconds = 300;

    /** 同一轮内的多个工具调用是否并发执行（关闭时保持串行语义） */
    @Builder.Default
    private boolean parallelTools = false;

    /** 轮次耗尽时是否再追加一次"请基于已有信息给出结论"的收尾调用 */
    @Builder.Default
    private boolean finalizeOnExhaust = false;

    /** 单次工具调用的兜底超时（秒），工具自身未配置时生效 */
    @Builder.Default
    private long toolTimeoutSeconds = 30;

    public boolean deadlineExceeded(long startMs) {
        return timeoutSeconds > 0 && System.currentTimeMillis() - startMs > timeoutSeconds * 1000L;
    }

    /**
     * 第 round 轮（从 1 开始）之后是否还允许继续循环
     */
    public boolean roundExhausted(int round) {
        return maxRounds > 0 && round >= maxRounds;
    }

    public static LoopPolicy of(int maxRounds, long timeoutSeconds) {
        return LoopPolicy.builder().maxRounds(maxRounds).timeoutSeconds(timeoutSeconds).build();
    }

    /** 子 Agent 使用的紧凑策略 */
    public static LoopPolicy tight(int maxRounds, long timeoutSeconds) {
        return LoopPolicy.builder()
                .maxRounds(maxRounds)
                .timeoutSeconds(timeoutSeconds)
                .parallelTools(false)
                .build();
    }
}
