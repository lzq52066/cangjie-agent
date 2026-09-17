package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import cn.cangjiecloud.observability.mapper.SystemMetricMapper;
import cn.cangjiecloud.observability.service.ISystemMetricService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMetricServiceImpl extends ServiceImpl<SystemMetricMapper, SystemMetricEntity>
        implements ISystemMetricService {

    @Value("${cangjie.observability.metrics.enabled:true}")
    private boolean metricsEnabled;

    /** 指标保留天数，超期数据由定时任务物理删除；<=0 表示不清理 */
    @Value("${cangjie.observability.metrics.retention-days:30}")
    private int retentionDays;

    /** 保留期清理的单批删除行数 */
    private static final int PURGE_BATCH_SIZE = 5000;

    /** 指标类型常量（受 system_metric.metric_type varchar(20) 限制，名称需保持简短） */
    private static final String TYPE_CPU = "cpu";
    private static final String TYPE_MEMORY = "memory";
    private static final String TYPE_MEMORY_POOL = "memory_pool";
    private static final String TYPE_BUFFER_POOL = "buffer_pool";
    private static final String TYPE_FILE_DESCRIPTOR = "file_descriptor";
    private static final String TYPE_THREAD = "thread";
    private static final String TYPE_DISK = "disk";
    private static final String TYPE_GC = "gc";
    private static final String TYPE_JVM = "jvm";

    /** 年轻代（Minor GC）收集器名称关键字，用于从收集器名称推断是否 Full GC */
    private static final List<String> MINOR_GC_KEYWORDS =
            List.of("young", "scavenge", "parnew", "copy", "minor", "pause");

    /** 需要统计的存活线程状态，NEW / TERMINATED 无监控意义，不计入 */
    private static final List<Thread.State> MONITORED_THREAD_STATES =
            List.of(Thread.State.RUNNABLE, Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING);

    @Override
    public List<SystemMetricEntity> collect() {
        String host = resolveHost();
        LocalDateTime now = LocalDateTime.now();
        List<SystemMetricEntity> metrics = new ArrayList<>();

        collectMemory(metrics, host, now);
        collectMemoryPool(metrics, host, now);
        collectBufferPool(metrics, host, now);
        collectThread(metrics, host, now);
        collectFileDescriptor(metrics, host, now);
        collectCpu(metrics, host, now);
        collectDisk(metrics, host, now);
        collectGc(metrics, host, now);
        collectJvm(metrics, host, now);

        saveBatch(metrics);
        log.info("系统指标采集完成，共 {} 条", metrics.size());
        return metrics;
    }

    @Override
    public IPage<SystemMetricEntity> pageQuery(String metricType, LocalDateTime startTime, LocalDateTime endTime,
                                               Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SystemMetricEntity> wrapper = new LambdaQueryWrapper<SystemMetricEntity>()
                .eq(StringUtils.hasText(metricType), SystemMetricEntity::getMetricType, metricType)
                .ge(startTime != null, SystemMetricEntity::getCollectTime, startTime)
                .le(endTime != null, SystemMetricEntity::getCollectTime, endTime)
                .orderByDesc(SystemMetricEntity::getCollectTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    /**
     * 定时采集：每 60 秒一次
     */
    @Scheduled(fixedDelay = 60000)
    public void scheduledCollect() {
        if (!metricsEnabled) return;
        try {
            collect();
        } catch (Exception e) {
            log.error("定时采集系统指标失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保留期清理：每天 03:30 分批物理删除超期指标。
     * 采集每 60 秒落库约 55 条（≈8 万行/天），无保留期会导致该表无界增长并拖慢按时间倒序的分页查询。
     */
    @Scheduled(cron = "0 30 3 * * ?")
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
            log.info("系统指标保留期清理完成，删除 {} 条（采集时间早于 {}）", total, cutoff);
        }
    }

    private void collectMemory(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        add(metrics, host, now, TYPE_MEMORY, "heap_used", bytesToMb(heap.getUsed()), "MB");
        add(metrics, host, now, TYPE_MEMORY, "heap_max", bytesToMb(heap.getMax()), "MB");
        add(metrics, host, now, TYPE_MEMORY, "heap_committed", bytesToMb(heap.getCommitted()), "MB");
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        add(metrics, host, now, TYPE_MEMORY, "non_heap_used", bytesToMb(nonHeap.getUsed()), "MB");
    }

    /**
     * 非堆内存池用量（Metaspace、压缩类空间、CodeHeap 各段）。
     * 堆内的池（如 ZGC 的 ZHeap）已由 memory 类型覆盖，此处跳过避免重复。
     */
    private void collectMemoryPool(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        try {
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getType() == MemoryType.HEAP) {
                    continue;
                }
                MemoryUsage usage = pool.getUsage();
                if (usage == null) {
                    continue;
                }
                String name = pool.getName();
                add(metrics, host, now, TYPE_MEMORY_POOL, name + "_used", bytesToMb(usage.getUsed()), "MB");
                // max 为 -1 表示该池没有上限（如未设 MaxMetaspaceSize 的 Metaspace），不记录无意义的上限值
                if (usage.getMax() >= 0) {
                    add(metrics, host, now, TYPE_MEMORY_POOL, name + "_max", bytesToMb(usage.getMax()), "MB");
                }
            }
        } catch (Exception e) {
            log.debug("内存池指标采集失败: {}", e.getMessage());
        }
    }

    /**
     * 堆外直接内存（DirectByteBuffer / MappedByteBuffer）用量，排查堆外内存泄漏的关键指标。
     */
    private void collectBufferPool(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        try {
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                String name = pool.getName();
                add(metrics, host, now, TYPE_BUFFER_POOL, name + "_count", (double) pool.getCount(), "个");
                add(metrics, host, now, TYPE_BUFFER_POOL, name + "_memory_used", bytesToMb(pool.getMemoryUsed()), "MB");
                add(metrics, host, now, TYPE_BUFFER_POOL, name + "_total_capacity", bytesToMb(pool.getTotalCapacity()), "MB");
            }
        } catch (Exception e) {
            log.debug("堆外缓冲区指标采集失败: {}", e.getMessage());
        }
    }

    private void collectThread(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        add(metrics, host, now, TYPE_THREAD, "thread_count", (double) thread.getThreadCount(), "个");
        add(metrics, host, now, TYPE_THREAD, "daemon_thread_count", (double) thread.getDaemonThreadCount(), "个");
        add(metrics, host, now, TYPE_THREAD, "peak_thread_count", (double) thread.getPeakThreadCount(), "个");
        add(metrics, host, now, TYPE_THREAD, "total_started_thread_count",
                (double) thread.getTotalStartedThreadCount(), "个");
        collectThreadStates(metrics, thread, host, now);
        collectDeadlockedThreads(metrics, thread, host, now);
    }

    /**
     * 存活线程按状态聚合的分布，用于区分线程数上涨是卡在锁上（BLOCKED）还是在等任务（WAITING）。
     */
    private void collectThreadStates(List<SystemMetricEntity> metrics, ThreadMXBean thread,
                                     String host, LocalDateTime now) {
        try {
            Map<Thread.State, Integer> counts = new EnumMap<>(Thread.State.class);
            for (Thread.State state : MONITORED_THREAD_STATES) {
                counts.put(state, 0);
            }
            // maxDepth 传 0 表示只取线程快照不采集栈，避免每次采样都付出抓栈的开销
            for (ThreadInfo info : thread.getThreadInfo(thread.getAllThreadIds(), 0)) {
                // 取 id 与取快照之间线程已退出的会是 null
                if (info == null) {
                    continue;
                }
                counts.computeIfPresent(info.getThreadState(), (state, count) -> count + 1);
            }
            for (Thread.State state : MONITORED_THREAD_STATES) {
                String name = state.name().toLowerCase(Locale.ROOT) + "_thread_count";
                add(metrics, host, now, TYPE_THREAD, name, (double) counts.get(state), "个");
            }
        } catch (Exception e) {
            log.debug("线程状态指标采集失败: {}", e.getMessage());
        }
    }

    private void collectDeadlockedThreads(List<SystemMetricEntity> metrics, ThreadMXBean thread,
                                          String host, LocalDateTime now) {
        try {
            long[] deadlocked = thread.findDeadlockedThreads();
            add(metrics, host, now, TYPE_THREAD, "deadlock_thread_count",
                    deadlocked == null ? 0 : deadlocked.length, "个");
        } catch (Exception e) {
            log.debug("死锁线程指标不可用: {}", e.getMessage());
        }
    }

    /**
     * 文件描述符用量，仅 Unix 平台可取（Windows 无该概念，直接跳过）。
     */
    private void collectFileDescriptor(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        try {
            if (os instanceof com.sun.management.UnixOperatingSystemMXBean unixOs) {
                long open = unixOs.getOpenFileDescriptorCount();
                long max = unixOs.getMaxFileDescriptorCount();
                add(metrics, host, now, TYPE_FILE_DESCRIPTOR, "open_file_descriptors", (double) open, "个");
                add(metrics, host, now, TYPE_FILE_DESCRIPTOR, "max_file_descriptors", (double) max, "个");
                add(metrics, host, now, TYPE_FILE_DESCRIPTOR, "file_descriptor_usage",
                        max > 0 ? percent((double) open / max) : -1, "%");
            }
        } catch (Exception e) {
            log.debug("文件描述符指标不可用: {}", e.getMessage());
        }
    }

    /**
     * JVM 自身状态：运行时长与类加载数量（类加载数持续增长通常意味着动态代理 / 反射在泄漏）。
     */
    private void collectJvm(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        add(metrics, host, now, TYPE_JVM, "uptime", runtime.getUptime() / 1000.0, "秒");
        ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();
        add(metrics, host, now, TYPE_JVM, "loaded_class_count", (double) classLoading.getLoadedClassCount(), "个");
        add(metrics, host, now, TYPE_JVM, "total_loaded_class_count",
                (double) classLoading.getTotalLoadedClassCount(), "个");
        add(metrics, host, now, TYPE_JVM, "unloaded_class_count", (double) classLoading.getUnloadedClassCount(), "个");
    }

    private void collectCpu(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        add(metrics, host, now, TYPE_CPU, "available_processors", (double) os.getAvailableProcessors(), "核");
        add(metrics, host, now, TYPE_CPU, "system_load_average", os.getSystemLoadAverage(), "");
        try {
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                add(metrics, host, now, TYPE_CPU, "process_cpu_load", percent(sunOs.getProcessCpuLoad()), "%");
                add(metrics, host, now, TYPE_CPU, "system_cpu_load", percent(sunOs.getCpuLoad()), "%");
            }
        } catch (Exception e) {
            log.debug("CPU 负载指标不可用: {}", e.getMessage());
        }
    }

    private void collectDisk(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                add(metrics, host, now, TYPE_DISK, store.name() + "_total", bytesToGb(store.getTotalSpace()), "GB");
                add(metrics, host, now, TYPE_DISK, store.name() + "_usable", bytesToGb(store.getUsableSpace()), "GB");
                long used = store.getTotalSpace() - store.getUsableSpace();
                add(metrics, host, now, TYPE_DISK, store.name() + "_used", bytesToGb(used), "GB");
            }
        } catch (Exception e) {
            log.debug("磁盘指标采集失败: {}", e.getMessage());
        }
    }

    private void collectGc(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        long fullGcCount = 0;
        long fullGcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String collectorName = gc.getName();
            add(metrics, host, now, TYPE_GC, collectorName + "_collection_count", (double) gc.getCollectionCount(), "次");
            add(metrics, host, now, TYPE_GC, collectorName + "_collection_time", (double) gc.getCollectionTime(), "ms");
            if (!isMinorGcCollector(collectorName)) {
                // 老年代 / 整堆回收，即通常意义上的 Full GC；-1 表示该指标不可用，按 0 处理
                fullGcCount += Math.max(0, gc.getCollectionCount());
                fullGcTime += Math.max(0, gc.getCollectionTime());
            }
        }
        add(metrics, host, now, TYPE_GC, "full_gc_count", (double) fullGcCount, "次");
        add(metrics, host, now, TYPE_GC, "full_gc_time", (double) fullGcTime, "ms");
    }

    /**
     * 判断是否为年轻代（Minor GC）收集器，剩下的都按老年代 / 整堆回收计入 Full GC。
     * <p>
     * HotSpot 各收集器没有统一的老年代标识，只能按名称特征区分：
     * G1 Young Generation / PS Scavenge / ParNew / Copy 为年轻代；
     * G1 Old Generation / PS MarkSweep / ConcurrentMarkSweep / Serial Old 为老年代；
     * ZGC 与 Shenandoah 这类非分代收集器每次回收整个堆，其 Cycles 计入 Full GC，
     * Pauses 只是同一次回收内的停顿次数（计入会与 Cycles 重复），故按年轻代排除。
     */
    private boolean isMinorGcCollector(String collectorName) {
        String lowerName = collectorName.toLowerCase(Locale.ROOT);
        return MINOR_GC_KEYWORDS.stream().anyMatch(lowerName::contains);
    }

    private void add(List<SystemMetricEntity> metrics, String host, LocalDateTime now,
                     String type, String name, double value, String unit) {
        SystemMetricEntity entity = new SystemMetricEntity();
        entity.setMetricType(type);
        entity.setMetricName(name);
        entity.setMetricValue(value);
        entity.setUnit(unit);
        entity.setHost(host);
        entity.setCollectTime(now);
        metrics.add(entity);
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private double bytesToMb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 * 100.0) / 100.0;
    }

    private double bytesToGb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 / 1024.0 * 100.0) / 100.0;
    }

    private double percent(double value) {
        return value < 0 ? -1 : Math.round(value * 10000.0) / 100.0;
    }
}