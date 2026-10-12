package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.observability.ExportedSpan;
import cn.cangjiecloud.core.observability.TraceExporter;
import cn.cangjiecloud.core.observability.TraceSpan;
import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceRecordServiceImpl extends ServiceImpl<TraceRecordMapper, TraceRecordEntity>
        implements ITraceRecordService {

    /**
     * span 保留天数，<=0 表示不清理。
     * <p>
     * 默认关：这是一条物理 DELETE，老库升级上来第一次调度就会把保留期之前的历史链路全删了，
     * 而升级的人通常不会预期「装个新版本 = 删历史」。要回收空间再显式开。
     */
    @Value("${cangjie.observability.trace.retention-days:0}")
    private int retentionDays;

    /** span 写入缓冲容量：满了丢新来的并计数，绝不把带载荷的同步写塞回业务线程 */
    @Value("${cangjie.observability.trace.write-queue-capacity:2000}")
    private int writeQueueCapacity;

    /**
     * span 写入线程池（独立、有界、拒绝即丢）。
     * <p>
     * 刻意不复用 businessExecutor：那个池是 CallerRunsPolicy，池满时任务会退回调用线程就地执行，
     * 而一条 span 可能挂着数十 KB 的检索/模型正文 —— 那等于把「不阻塞主链路」又请了回来，
     * 而且抢的是业务线程。这里宁可丢一条观测数据。
     */
    private Executor spanWriter;

    /** 因缓冲满丢掉的 span 条数 */
    private final AtomicLong droppedSpans = new AtomicLong();

    private volatile long lastReportedDropped;

    @PostConstruct
    void initWriter() {
        int capacity = Math.max(1, writeQueueCapacity);
        spanWriter = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "trace-span-writer");
                    // 守护线程：可观测性后台任务不该拖住进程退出
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdownWriter() {
        if (!(spanWriter instanceof ThreadPoolExecutor executor)) {
            return;
        }
        executor.shutdown();
        try {
            // 排障时最后几条 span 往往正是要看的，给它们一点时间落库
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** span 写入缓冲当前堆积数（仅供监控与单测；同步执行器没有队列，报 0） */
    int writerQueueSize() {
        return spanWriter instanceof ThreadPoolExecutor executor ? executor.getQueue().size() : 0;
    }

    /** 缓冲满丢掉的 span 条数 */
    long droppedSpanCount() {
        return droppedSpans.get();
    }

    /**
     * 外部出口（OTLP 等）：没配就一个也不装，默认空列表。
     * <p>
     * 外导排在落库之前：出口自己缓冲，而库写失败时这条 span 在本地已经没了，
     * 外部系统至少还能留下完整链路。
     */
    @Autowired(required = false)
    private List<TraceExporter> traceExporters = Collections.emptyList();

    private static final int PURGE_BATCH_SIZE = 2000;

    @Override
    public void record(TraceRecordEntity entity) {
        if (entity == null) {
            return;
        }
        export(entity);
        // 异步独立落库：span 不能跟着业务事务一起回滚，否则失败调用反而查不到
        try {
            spanWriter.execute(() -> {
                try {
                    save(entity);
                } catch (Exception ex) {
                    log.warn("trace_record 写入失败: module={}, action={}, {}",
                            entity.getModule(), entity.getAction(), ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            // 缓冲满了：丢这一条并计数。可观测性数据不值得让业务线程等库，也不值得为它撑爆堆
            droppedSpans.incrementAndGet();
            reportDropped();
        }
    }

    /** 丢弃只报增量：每次重报同一个累计值会把日志顶成刷屏 */
    private void reportDropped() {
        long total = droppedSpans.get();
        if (total > lastReportedDropped) {
            log.warn("trace_record 写入缓冲已满，累计丢弃 {} 条 span（本轮新增 {} 条），"
                    + "持续出现请调大 cangjie.observability.trace.write-queue-capacity",
                    total, total - lastReportedDropped);
            lastReportedDropped = total;
        }
    }

    /** 交给各出口：单个出口抛异常不能连带影响其他出口与落库 */
    private void export(TraceRecordEntity entity) {
        if (traceExporters == null || traceExporters.isEmpty()) {
            return;
        }
        ExportedSpan span = new ExportedSpan(entity.getTraceId(), entity.getSpanId(), entity.getParentSpanId(),
                entity.getServiceName(), entity.getModule(), entity.getAction(), entity.getStatus(),
                entity.getMessage(), toEpochMs(entity.getStartTime()),
                entity.getDuration() == null ? 0L : entity.getDuration(),
                entity.getInputPayload(), entity.getOutputPayload(), parseMetadata(entity.getMetadata()));
        for (TraceExporter exporter : traceExporters) {
            try {
                exporter.export(span);
            } catch (Exception ex) {
                log.warn("span 出口失败: exporter={}, {}", exporter.name(), ex.toString());
            }
        }
    }

    private static long toEpochMs(LocalDateTime time) {
        return time == null ? System.currentTimeMillis() : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** metadata 列本来就是 JSON 文本，解不成 Map 时交 null：出口拿不到维度不致命 */
    private static Map<String, Object> parseMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JsonUtils.parseMap(json);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public IPage<TraceRecordEntity> pageQuery(TraceQueryDTO query) {
        LambdaQueryWrapper<TraceRecordEntity> wrapper = briefWrapper()
                .eq(StringUtils.hasText(query.getTraceId()), TraceRecordEntity::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getModule()), TraceRecordEntity::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getAction()), TraceRecordEntity::getAction, query.getAction())
                .ge(query.getStartTime() != null, TraceRecordEntity::getStartTime, query.getStartTime())
                .le(query.getEndTime() != null, TraceRecordEntity::getStartTime, query.getEndTime())
                .orderByDesc(TraceRecordEntity::getStartTime);
        return page(new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                query.getPageSize() == null ? 10 : query.getPageSize()), wrapper);
    }

    /**
     * 只选精简列的 wrapper：载荷三列单条可达数十 KB，列表/详情都别带上，
     * 正文由 {@link #payload(String)} 按 spanId 单条拉取。
     */
    private LambdaQueryWrapper<TraceRecordEntity> briefWrapper() {
        return new LambdaQueryWrapper<TraceRecordEntity>()
                .select(TraceRecordEntity.class,
                        info -> !TraceRecordEntity.PAYLOAD_COLUMNS.contains(info.getColumn()));
    }

    /**
     * TraceCollector 实现：将子 span 写入 trace_record 表，供 AOP / 手动调用。
     * <p>
     * 父子关系靠「入口 span 的 spanId == traceId」这一约定还原，无需额外的线程上下文：
     * 同链路内的子 span 直接把 parentSpanId 指回 traceId 即可挂到入口 span 下。
     */
    @Override
    public void record(String module, String action, String traceId, long duration, String status, String message) {
        record(TraceSpan.of(module, action, traceId, duration, status, message));
    }

    /**
     * 带载荷的子 span：除了耗时与状态，还把本次节点的输入 / 输出原样落库。
     * <p>
     * 载荷序列化失败不能把整个 span 一起丢掉，因此逐字段兜底：丢了正文至少还留着耗时。
     */
    @Override
    public void record(TraceSpan span) {
        if (span == null) {
            return;
        }
        TraceRecordEntity entity = buildSpan(span.getModule(), span.getAction(), span.getTraceId(),
                span.getDuration(), span.getStatus(), span.getMessage());
        entity.setSpanId(UUID.randomUUID().toString().replace("-", ""));
        entity.setParentSpanId(span.getTraceId());
        entity.setInputPayload(span.getInput());
        entity.setOutputPayload(span.getOutput());
        entity.setMetadata(toJsonQuietly(span.getMetadata()));
        record(entity);
    }

    @Override
    public void recordEntry(String module, String action, String traceId, long duration,
                            String status, String message) {
        TraceRecordEntity entity = buildSpan(module, action, traceId, duration, status, message);
        // 入口 span 用 traceId 自身作 spanId：子 span 只带着 traceId 也能定位到父节点
        entity.setSpanId(StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString().replace("-", ""));
        record(entity);
    }

    @Override
    public TraceRecordEntity payload(String spanId) {
        if (!StringUtils.hasText(spanId)) {
            return null;
        }
        // 入口 span 的 spanId 就是 traceId，同链路内子 span 的 spanId 全局唯一，按 spanId 取即可
        return getOne(new LambdaQueryWrapper<TraceRecordEntity>()
                .eq(TraceRecordEntity::getSpanId, spanId)
                .last("limit 1"));
    }

    /**
     * 保留期清理：每天 03:45 分批物理删除超期 span。
     * <p>
     * 载荷三列（含用户 query 与知识库正文片段）只能靠物理删除回收空间，
     * 不然 trace_record 会随对话量单调增长。
     */
    @Scheduled(cron = "0 45 3 * * ?")
    public void scheduledPurge() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        int removed;
        do {
            removed = baseMapper.purgeBatch(cutoff, PURGE_BATCH_SIZE);
            total += removed;
        } while (removed == PURGE_BATCH_SIZE);
        if (total > 0) {
            log.info("trace_record 保留期清理完成，删除 {} 条（开始时间早于 {}）", total, cutoff);
        }
    }

    /** 载荷序列化失败时返回 null，仅丢正文不影响 span 本身 */
    private String toJsonQuietly(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.toJSONString(metadata);
        } catch (Exception ex) {
            log.warn("span metadata 序列化失败: {}", ex.toString());
            return null;
        }
    }

    private TraceRecordEntity buildSpan(String module, String action, String traceId, long duration,
                                        String status, String message) {
        TraceRecordEntity entity = new TraceRecordEntity();
        entity.setTraceId(traceId);
        entity.setModule(module);
        entity.setAction(action);
        entity.setDuration(duration);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setServiceName("cangjie-agent");
        entity.setStartTime(LocalDateTime.now());
        return entity;
    }
}