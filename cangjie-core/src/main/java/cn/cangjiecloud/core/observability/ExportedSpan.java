package cn.cangjiecloud.core.observability;

import java.util.Map;

/**
 * 交给出口的一条 span：在 {@link TraceSpan} 之上补齐了「外部观测系统必须要、但我们自己落库时才生成」的三样东西
 * —— spanId、parentSpanId 与绝对开始时间。
 * <p>
 * 为什么不直接把 {@code trace_record} 的实体丢给出口：那会把列名与库结构拖进抽象层，
 * 换存储就得改出口。时间用 epoch 毫秒而不是 LocalDateTime：OTLP 只认绝对时间，
 * 本地时区换一次就把所有直方图挪位。
 *
 * @param traceId      链路 ID
 * @param spanId       本 span ID，入口 span 约定等于 traceId
 * @param parentSpanId 父 span ID，入口 span 为 null
 * @param serviceName  服务名，OTLP 的 {@code service.name} 资源属性
 * @param module       模块名（chat / retrieval …）
 * @param action       动作（send / search …）
 * @param status       success / fail
 * @param message      概要描述
 * @param startEpochMs 开始时间（epoch 毫秒）
 * @param durationMs   耗时（毫秒）
 * @param input        输入载荷 JSON，可为 null
 * @param output       输出载荷 JSON，可为 null
 * @param metadata     附加维度，可为 null
 */
public record ExportedSpan(String traceId,
                           String spanId,
                           String parentSpanId,
                           String serviceName,
                           String module,
                           String action,
                           String status,
                           String message,
                           long startEpochMs,
                           long durationMs,
                           String input,
                           String output,
                           Map<String, Object> metadata) {

    public static ExportedSpan of(TraceSpan span, String spanId, String parentSpanId,
                                  String serviceName, long startEpochMs) {
        return new ExportedSpan(span == null ? null : span.getTraceId(), spanId, parentSpanId, serviceName,
                span == null ? null : span.getModule(), span == null ? null : span.getAction(),
                span == null ? null : span.getStatus(), span == null ? null : span.getMessage(),
                startEpochMs, span == null ? 0L : span.getDuration(),
                span == null ? null : span.getInput(), span == null ? null : span.getOutput(),
                span == null ? null : span.getMetadata());
    }

    public long endEpochMs() {
        return startEpochMs + Math.max(0L, durationMs);
    }

    public boolean failed() {
        return "fail".equalsIgnoreCase(status);
    }
}
