package cn.cangjiecloud.core.observability;

/**
 * Span 出口 SPI：把本地链路数据推给外部观测系统（OTLP / Jaeger / 未来的消息队列）。
 * <p>
 * 落库与外导是两条独立的路：{@code trace_record} 是「我们自己看得懂」的存储，
 * 出口是给「已经有一整套告警与看板」的系统用的。做成 SPI 而不是直接在写入处发 HTTP，
 * 是为了让没配出口的环境一个 bean 都不装，配了出口的环境不必改业务代码。
 * <p>
 * 实现方必须自己保证不拖住调用线程：这一层挂在每个 span 的落库点上，
 * 出口抖动（对端超时、限流）如果能把对话主链路拖慢，可观测性就成了故障源。
 */
public interface TraceExporter {

    /**
     * 交出一个已落库的 span。
     * <p>
     * 实现方通常只入队、由后台线程批量发送；这里不允许抛异常向外传播，
     * 出口失败应当被吞掉并计数，而不是把业务调用一起带下去。
     */
    void export(ExportedSpan span);

    /** 出口名，用于日志与多出口并存时定位 */
    default String name() {
        return getClass().getSimpleName();
    }
}
