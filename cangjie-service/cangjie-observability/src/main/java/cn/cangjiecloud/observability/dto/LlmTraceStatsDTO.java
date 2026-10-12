package cn.cangjiecloud.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 时间窗口内的模型调用统计（观测面板用）。
 * <p>
 * 三个口径各管一件事：P95 看体感、错误率看稳定性、成本看钱花在哪。
 * 只给平均值会同时骗过这三件事 —— 少数几次超时把均值抹平、错误被大分母稀释、
 * 而总成本看不出是哪个模型吃掉的。
 */
@Data
@Builder
public class LlmTraceStatsDTO {

    /** 调用总数 */
    private long calls;

    /** 失败调用数 */
    private long failures;

    /** 错误率（0~1，保留 4 位）：没有调用时为 0，不是 null —— 面板要能直接乘 100 显示 */
    private BigDecimal errorRate;

    /** 估算成本合计（元）：模型没维护单价的调用不计入，因此「0」要连着 calls 一起看 */
    private BigDecimal cost;

    /** token 合计 */
    private long tokens;

    /** 平均耗时（毫秒） */
    private Double avgDuration;

    /** 中位耗时（毫秒） */
    private Double p50Duration;

    /** P95 耗时（毫秒） */
    private Double p95Duration;

    /** 带首字延迟的调用数（流式调用数）：为 0 时下面两个字段没有意义 */
    private long streamingCalls;

    /** 平均首字延迟（毫秒） */
    private Double avgTtft;

    /** P95 首字延迟（毫秒） */
    private Double p95Ttft;

    /** 按模型切片 */
    private List<ModelStat> byModel;

    /**
     * 单个模型的调用统计。
     */
    @Data
    @Builder
    public static class ModelStat {

        /** 模型显示名（无名时归到「未知模型」） */
        private String modelName;

        private long calls;

        private long failures;

        private BigDecimal errorRate;

        private BigDecimal cost;

        private long tokens;

        private Double p95Duration;
    }
}
