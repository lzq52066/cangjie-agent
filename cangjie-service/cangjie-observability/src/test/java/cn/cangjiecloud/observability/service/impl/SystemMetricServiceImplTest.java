package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SystemMetricServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code saveBatch/page} 等继承自 MyBatis-Plus {@code ServiceImpl} 的方法，
 * 不注入真实的 SqlSession，不连接数据库。
 */
class SystemMetricServiceImplTest {

    private SystemMetricServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.observability.mapper.SystemMetricMapper");
        TableInfoHelper.initTableInfo(assistant, SystemMetricEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new SystemMetricServiceImpl());
    }

    // ---------- collect ----------

    @Test
    void collectShouldGatherCoreJvmMetricsAndPersist() {
        doReturn(true).when(service).saveBatch(anyCollection());

        List<SystemMetricEntity> metrics = service.collect();

        assertThat(metrics).isNotEmpty();
        Set<String> types = metrics.stream().map(SystemMetricEntity::getMetricType).collect(Collectors.toSet());
        assertThat(types).contains("memory", "thread", "cpu", "gc", "jvm", "disk");

        // 至少包含堆内存与关键线程指标
        Set<String> names = metrics.stream().map(SystemMetricEntity::getMetricName).collect(Collectors.toSet());
        assertThat(names).contains("heap_used", "heap_max", "heap_committed", "non_heap_used");
        assertThat(names).contains("thread_count", "daemon_thread_count", "peak_thread_count");
        assertThat(names).contains("available_processors");
        assertThat(names).contains("full_gc_count", "full_gc_time");
        assertThat(names).contains("uptime", "loaded_class_count");

        // 每条指标都填充了 host / collectTime / value
        assertThat(metrics).allSatisfy(m -> {
            assertThat(m.getHost()).isNotBlank();
            assertThat(m.getCollectTime()).isNotNull();
            assertThat(m.getMetricValue()).isNotNull();
        });

        verify(service, times(1)).saveBatch(metrics);
    }

    @Test
    void collectShouldAlwaysEmitFourThreadStateBuckets() {
        doReturn(true).when(service).saveBatch(anyCollection());

        List<SystemMetricEntity> metrics = service.collect();
        Set<String> names = metrics.stream().map(SystemMetricEntity::getMetricName).collect(Collectors.toSet());

        assertThat(names).contains("runnable_thread_count", "blocked_thread_count",
                "waiting_thread_count", "timed_waiting_thread_count");
    }

    // ---------- pageQuery ----------

    @Test
    void pageQueryShouldApplyAllFiltersAndOrderAndUseDefaults() {
        IPage<SystemMetricEntity> stub = new Page<>(1, 10);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 31, 0, 0);
        IPage<SystemMetricEntity> result = service.pageQuery("cpu", start, end, 2, 20);

        assertThat(result).isSameAs(stub);

        ArgumentCaptor<IPage<SystemMetricEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<SystemMetricEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());

        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(2);
        assertThat(requested.getSize()).isEqualTo(20);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        String sql = wrapper.getTargetSql() + " | ORDER:" + wrapper.getSqlSegment();
        assertThat(sql).contains("metric_type");
        assertThat(sql).contains("collect_time");
        assertThat(sql).containsIgnoringCase("ORDER BY");
    }

    @Test
    void pageQueryShouldFallBackToDefaultsWhenPagingArgsAreNullAndSkipEmptyFilters() {
        IPage<SystemMetricEntity> stub = new Page<>(1, 10);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        service.pageQuery(null, null, null, null, null);

        ArgumentCaptor<IPage<SystemMetricEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<SystemMetricEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());

        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(1);
        assertThat(requested.getSize()).isEqualTo(10);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        // 无过滤条件时只保留 orderBy；targetSql 里没有 where 段的列名
        String targetSql = wrapper.getTargetSql();
        assertThat(targetSql).doesNotContain("metric_type");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
    }

    // ---------- scheduledCollect ----------

    @Test
    void scheduledCollectShouldDoNothingWhenDisabled() {
        ReflectionTestUtils.setField(service, "metricsEnabled", false);

        service.scheduledCollect();

        verify(service, never()).saveBatch(anyCollection());
        verify(service, never()).collect();
    }

    @Test
    void scheduledCollectShouldTriggerCollectWhenEnabled() {
        ReflectionTestUtils.setField(service, "metricsEnabled", true);
        doReturn(List.<SystemMetricEntity>of()).when(service).collect();

        service.scheduledCollect();

        verify(service, times(1)).collect();
    }

    @Test
    void scheduledCollectShouldSwallowExceptionFromCollect() {
        ReflectionTestUtils.setField(service, "metricsEnabled", true);
        doThrow(new RuntimeException("boom")).when(service).collect();

        assertThatCode(() -> service.scheduledCollect()).doesNotThrowAnyException();
        verify(service, times(1)).collect();
    }

    // ---------- private helpers ----------

    @Test
    void percentShouldClampNegativeAndRoundHundredths() throws Exception {
        Method m = SystemMetricServiceImpl.class.getDeclaredMethod("percent", double.class);
        m.setAccessible(true);
        assertThat((double) m.invoke(service, -0.5)).isEqualTo(-1.0);
        assertThat((double) m.invoke(service, 0.0)).isZero();
        assertThat((double) m.invoke(service, 0.1234)).isEqualTo(12.34);
    }

    @Test
    void bytesToMbAndGbShouldRoundToTwoDecimals() throws Exception {
        Method mb = SystemMetricServiceImpl.class.getDeclaredMethod("bytesToMb", long.class);
        Method gb = SystemMetricServiceImpl.class.getDeclaredMethod("bytesToGb", long.class);
        mb.setAccessible(true);
        gb.setAccessible(true);
        assertThat((double) mb.invoke(service, 0L)).isZero();
        assertThat((double) mb.invoke(service, 1024L * 1024L)).isEqualTo(1.0);
        assertThat((double) gb.invoke(service, 1024L * 1024L * 1024L)).isEqualTo(1.0);
        // 1KB -> 0.001MB，四舍五入两位 -> 0.0
        assertThat((double) mb.invoke(service, 1024L)).isZero();
    }

    @Test
    void isMinorGcCollectorShouldMatchKeywordsCaseInsensitively() throws Exception {
        Method m = SystemMetricServiceImpl.class.getDeclaredMethod("isMinorGcCollector", String.class);
        m.setAccessible(true);
        assertThat((boolean) m.invoke(service, "G1 Young Generation")).isTrue();
        assertThat((boolean) m.invoke(service, "PS Scavenge")).isTrue();
        assertThat((boolean) m.invoke(service, "ParNew")).isTrue();
        assertThat((boolean) m.invoke(service, "ZGC Pause")).isTrue();
        assertThat((boolean) m.invoke(service, "G1 Old Generation")).isFalse();
        assertThat((boolean) m.invoke(service, "ConcurrentMarkSweep")).isFalse();
        assertThat((boolean) m.invoke(service, "Shenandoah Cycles")).isFalse();
    }
}
