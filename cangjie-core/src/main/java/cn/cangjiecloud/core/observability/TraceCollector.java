package cn.cangjiecloud.core.observability;

/**
 * 调用追踪采集 SPI。
 * <p>
 * 由具体实现（如写入 trace_record 表的 TraceRecordServiceImpl）注册为 Spring Bean，
 * 供 AOP 切面或业务代码手动调用，记录对话 / 检索等关键链路的 traceId + span 耗时。
 */
public interface TraceCollector {

    /**
     * 记录一条追踪 span
     *
     * @param module   模块名（如 chat / retrieval）
     * @param action   动作（如 send / search）
     * @param traceId  链路 ID
     * @param duration 耗时（毫秒）
     * @param status   状态：success / fail
     * @param message  描述信息
     */
    void record(String module, String action, String traceId, long duration, String status, String message);
}
