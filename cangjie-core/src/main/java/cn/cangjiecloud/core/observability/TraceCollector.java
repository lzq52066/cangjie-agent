package cn.cangjiecloud.core.observability;

/**
 * 调用追踪采集 SPI。
 * <p>
 * 由具体实现（如写入 trace_record 表的 TraceRecordServiceImpl）注册为 Spring Bean，
 * 供 AOP 切面或业务代码手动调用，记录对话 / 检索等关键链路的 traceId + span 耗时。
 */
public interface TraceCollector {

    /**
     * 记录一条链路内的子 span。
     * <p>
     * 实现方应把它挂到该 traceId 的入口 span 下：一条链路只有一个入口，
     * 因此入口 span 的 spanId 约定等于 traceId，子 span 靠该约定回填 parentSpanId。
     *
     * @param module   模块名（如 chat / retrieval）
     * @param action   动作（如 send / search）
     * @param traceId  链路 ID
     * @param duration 耗时（毫秒）
     * @param status   状态：success / fail
     * @param message  描述信息
     */
    void record(String module, String action, String traceId, long duration, String status, String message);

    /**
     * 记录一条带输入 / 输出载荷的子 span。
     * <p>
     * 载荷用于把「一次节点执行」变成可复现的证据（检索用了什么 query、召回了什么、分数如何），
     * 默认实现忽略载荷退化成纯耗时 span，因此未升级的实现方照样能跑，只是排障信息少一截。
     */
    default void record(TraceSpan span) {
        if (span == null) {
            return;
        }
        record(span.getModule(), span.getAction(), span.getTraceId(), span.getDuration(),
                span.getStatus(), span.getMessage());
    }

    /**
     * 记录一条链路的入口 span（请求级端到端 span，如一次对话、一次工作流执行）。
     * <p>
     * 与 {@link #record} 的唯一区别是它在 span 树上没有父节点。老实现未覆写时退化为普通 span，
     * 只是链路还原不出层级，不影响落库。
     */
    default void recordEntry(String module, String action, String traceId, long duration,
                             String status, String message) {
        record(module, action, traceId, duration, status, message);
    }
}
