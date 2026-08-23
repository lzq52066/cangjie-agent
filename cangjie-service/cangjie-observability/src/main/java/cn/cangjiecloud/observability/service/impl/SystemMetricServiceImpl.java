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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMetricServiceImpl extends ServiceImpl<SystemMetricMapper, SystemMetricEntity>
        implements ISystemMetricService {

    @Value("${cangjie.observability.metrics.enabled:true}")
    private boolean metricsEnabled;

    /** 指标类型常量 */
    private static final String TYPE_CPU = "cpu";
    private static final String TYPE_MEMORY = "memory";
    private static final String TYPE_THREAD = "thread";
    private static final String TYPE_DISK = "disk";
    private static final String TYPE_GC = "gc";

    @Override
    public List<SystemMetricEntity> collect() {
        String host = resolveHost();
        LocalDateTime now = LocalDateTime.now();
        List<SystemMetricEntity> metrics = new ArrayList<>();

        collectMemory(metrics, host, now);
        collectThread(metrics, host, now);
        collectCpu(metrics, host, now);
        collectDisk(metrics, host, now);
        collectGc(metrics, host, now);

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

    private void collectMemory(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        add(metrics, host, now, TYPE_MEMORY, "heap_used", bytesToMb(heap.getUsed()), "MB");
        add(metrics, host, now, TYPE_MEMORY, "heap_max", bytesToMb(heap.getMax()), "MB");
        add(metrics, host, now, TYPE_MEMORY, "heap_committed", bytesToMb(heap.getCommitted()), "MB");
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        add(metrics, host, now, TYPE_MEMORY, "non_heap_used", bytesToMb(nonHeap.getUsed()), "MB");
    }

    private void collectThread(List<SystemMetricEntity> metrics, String host, LocalDateTime now) {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        add(metrics, host, now, TYPE_THREAD, "thread_count", (double) thread.getThreadCount(), "个");
        add(metrics, host, now, TYPE_THREAD, "daemon_thread_count", (double) thread.getDaemonThreadCount(), "个");
        add(metrics, host, now, TYPE_THREAD, "peak_thread_count", (double) thread.getPeakThreadCount(), "个");
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
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            add(metrics, host, now, TYPE_GC, gc.getName() + "_collection_count", (double) gc.getCollectionCount(), "次");
            add(metrics, host, now, TYPE_GC, gc.getName() + "_collection_time", (double) gc.getCollectionTime(), "ms");
        }
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