package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.dto.LlmTraceStatsDTO;
import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.entity.SystemMetricEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.ObservabilityStatsMapper;
import cn.cangjiecloud.observability.service.ILlmTraceStatsService;
import cn.cangjiecloud.observability.service.IOperationLogService;
import cn.cangjiecloud.observability.service.ISystemMetricService;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ObservabilityController} 单元测试：验证日志/指标/追踪查询的参数透传、
 * chart 的时间窗口兜底与分组整形、dashboard 的统计聚合与条件拼接。
 */
@ExtendWith(MockitoExtension.class)
class ObservabilityControllerTest {

    private static final int MAX_CHART_ROWS = 5000;

    @Mock
    private IOperationLogService operationLogService;

    @Mock
    private ISystemMetricService systemMetricService;

    @Mock
    private ITraceRecordService traceRecordService;

    @Mock
    private ObservabilityStatsMapper observabilityStatsMapper;

    @Mock
    private ILlmTraceStatsService llmTraceStatsService;

    private ObservabilityController controller;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.observability.mapper.ObservabilityStatsMapper");
        TableInfoHelper.initTableInfo(assistant, SystemMetricEntity.class);
        TableInfoHelper.initTableInfo(assistant, OperationLogEntity.class);
        TableInfoHelper.initTableInfo(assistant, TraceRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        controller = new ObservabilityController(operationLogService, systemMetricService,
                traceRecordService, observabilityStatsMapper, llmTraceStatsService);
    }

    private static SystemMetricEntity metric(String type, String name, String unit,
                                             Double value, LocalDateTime time) {
        SystemMetricEntity entity = new SystemMetricEntity();
        entity.setMetricType(type);
        entity.setMetricName(name);
        entity.setUnit(unit);
        entity.setMetricValue(value);
        entity.setCollectTime(time);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private void stubChartPage(List<SystemMetricEntity> rows) {
        Page<SystemMetricEntity> page = new Page<>(1, MAX_CHART_ROWS, false);
        page.setRecords(rows);
        doReturn(page).when(systemMetricService).page(any(Page.class), any(Wrapper.class));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groupsOf(R<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getData().get("groups");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> seriesOf(Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("series");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dataOf(Map<String, Object> series) {
        return (List<Map<String, Object>>) series.get("data");
    }

    @Nested
    class Logs {

        @Test
        void shouldDelegateAllFiltersAndWrapPage() {
            // 正常场景：模块/动作/状态/分页参数原样传给 Service
            Page<OperationLogEntity> page = new Page<>(2, 50);
            page.setTotal(120L);
            when(operationLogService.pageQuery("user", "login", "fail", 2, 50)).thenReturn(page);

            R<IPage<OperationLogEntity>> response = controller.logs("user", "login", "fail", 2, 50);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            verify(operationLogService).pageQuery("user", "login", "fail", 2, 50);
        }

        @Test
        void shouldPassNullFiltersAndExplicitPagingThrough() {
            // 边界场景：过滤条件可空；默认分页由 @RequestParam 提供，此处只测透传
            Page<OperationLogEntity> page = new Page<>(1, 10);
            when(operationLogService.pageQuery(null, null, null, 1, 10)).thenReturn(page);

            R<IPage<OperationLogEntity>> response = controller.logs(null, null, null, 1, 10);

            assertThat(response.getData()).isSameAs(page);
            verify(operationLogService).pageQuery(null, null, null, 1, 10);
        }
    }

    @Nested
    class Metrics {

        @Test
        void shouldDelegateTypeTimeRangeAndPaging() {
            // 正常场景：指标类型与时间范围原样传给 Service
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0);
            Page<SystemMetricEntity> page = new Page<>(1, 10);
            when(systemMetricService.pageQuery("cpu", start, end, 1, 10)).thenReturn(page);

            R<IPage<SystemMetricEntity>> response = controller.metrics("cpu", start, end, 1, 10);

            assertThat(response.getData()).isSameAs(page);
            verify(systemMetricService).pageQuery("cpu", start, end, 1, 10);
        }

        @Test
        void shouldWrapCollectedMetrics() {
            // 正常场景：手动采集直接返回本次采集结果
            List<SystemMetricEntity> collected = List.of(metric("cpu", "usage", "%", 1.0, null));
            when(systemMetricService.collect()).thenReturn(collected);

            R<List<SystemMetricEntity>> response = controller.collect();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(collected);
            verify(systemMetricService).collect();
        }
    }

    @Nested
    class Traces {

        @Test
        void shouldDelegateTraceQueryDto() {
            // 正常场景：追踪查询 DTO 原样传给 Service
            TraceQueryDTO query = new TraceQueryDTO();
            query.setModule("chat");
            query.setPageNum(2);
            query.setPageSize(10);
            Page<TraceRecordEntity> page = new Page<>(2, 10);
            when(traceRecordService.pageQuery(query)).thenReturn(page);

            R<IPage<TraceRecordEntity>> response = controller.traces(query);

            assertThat(response.getData()).isSameAs(page);
            verify(traceRecordService).pageQuery(query);
        }
    }

    @Nested
    class Chart {

        @Test
        void shouldApplyDefaultWindowReverseRowsAndGroupByTypeAndName() {
            // 正常场景：缺省时间窗为最近 2 小时；倒序明细反转成升序并按 type/name 分组
            LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
            LocalDateTime t2 = LocalDateTime.of(2026, 1, 1, 10, 5);
            LocalDateTime t3 = LocalDateTime.of(2026, 1, 1, 10, 10);
            // Service 返回按 collect_time 倒序的最新行
            stubChartPage(List.of(
                    metric("memory", "heap_used", "MB", 300.0, t3),
                    metric("cpu", "usage", "%", 12.5, t2),
                    metric("cpu", "usage", "%", 10.0, t1)));

            R<Map<String, Object>> response = controller.chart(null, null, null);

            assertThat(response.isSuccess()).isTrue();

            // 单次读取按 maxLimit 截断、不做 count
            ArgumentCaptor<IPage<SystemMetricEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<SystemMetricEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(systemMetricService).page(pageCap.capture(), wrapperCap.capture());
            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(MAX_CHART_ROWS);
            assertThat(requested.searchCount()).isFalse();

            // 无 metricType 时不拼类型条件；时间窗命中 collect_time，倒序取最新
            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).contains("collect_time").doesNotContain("metric_type");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");

            // 时间参数为 now-2h ~ now
            List<LocalDateTime> window = wrapper.getParamNameValuePairs().values().stream()
                    .map(LocalDateTime.class::cast)
                    .sorted()
                    .toList();
            assertThat(window).hasSize(2);
            assertThat(window.get(0)).isAfter(LocalDateTime.now().minusHours(2).minusMinutes(5));
            assertThat(window.get(1)).isAfter(LocalDateTime.now().minusMinutes(5));

            // 反转后按 collect_time 升序，且分组顺序为首次出现顺序
            List<Map<String, Object>> groups = groupsOf(response);
            assertThat(groups).hasSize(2);
            Map<String, Object> cpuGroup = groups.get(0);
            assertThat(cpuGroup).containsEntry("metricType", "cpu");
            List<Map<String, Object>> cpuSeries = seriesOf(cpuGroup);
            assertThat(cpuSeries).hasSize(1);
            assertThat(cpuSeries.get(0)).containsEntry("name", "usage").containsEntry("unit", "%");
            List<Map<String, Object>> points = dataOf(cpuSeries.get(0));
            assertThat(points).hasSize(2);
            assertThat(points.get(0)).containsEntry("time", t1.toString()).containsEntry("value", 10.0);
            assertThat(points.get(1)).containsEntry("time", t2.toString()).containsEntry("value", 12.5);

            Map<String, Object> memoryGroup = groups.get(1);
            assertThat(memoryGroup).containsEntry("metricType", "memory");
            List<Map<String, Object>> memoryPoints = dataOf(seriesOf(memoryGroup).get(0));
            assertThat(memoryPoints).hasSize(1);
            assertThat(memoryPoints.get(0)).containsEntry("value", 300.0);
        }

        @Test
        void shouldFilterByMetricTypeAndPassExplicitRange() {
            // 正常场景：显式 metricType 与时间范围入条件；同类型不同 name 生成多 series
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 1, 1, 12, 0);
            stubChartPage(List.of(
                    metric("cpu", "load", "", 0.5, start.plusHours(2)),
                    metric("cpu", "usage", "%", 1.0, start.plusHours(1))));

            R<Map<String, Object>> response = controller.chart("cpu", start, end);

            ArgumentCaptor<Wrapper<SystemMetricEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(systemMetricService).page(any(Page.class), wrapperCap.capture());
            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).contains("metric_type");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("cpu", start, end);

            List<Map<String, Object>> groups = groupsOf(response);
            assertThat(groups).hasSize(1);
            List<Map<String, Object>> series = seriesOf(groups.get(0));
            assertThat(series).hasSize(2);
            // 反转后 usage(1h) 在前、load(2h) 在后
            assertThat(series.get(0)).containsEntry("name", "usage");
            assertThat(series.get(1)).containsEntry("name", "load");
        }

        @Test
        void shouldEmitEmptyTimeStringWhenCollectTimeMissing() {
            // 边界场景：collect_time 为空的脏数据不会 NPE，时间位补空串
            stubChartPage(List.of(metric("jvm", "uptime", "ms", 1234.0, null)));

            R<Map<String, Object>> response = controller.chart(null, null, null);

            List<Map<String, Object>> points = dataOf(seriesOf(groupsOf(response).get(0)).get(0));
            assertThat(points).hasSize(1);
            assertThat(points.get(0)).containsEntry("time", "").containsEntry("value", 1234.0);
        }

        @Test
        void shouldReturnEmptyGroupsWhenNoMetricMatched() {
            // 边界场景：窗口内无指标返回空 groups 而非失败或 null
            stubChartPage(List.of());

            R<Map<String, Object>> response = controller.chart("cpu", null, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(groupsOf(response)).isEmpty();
        }
    }

    @Nested
    class Dashboard {

        @Test
        void shouldAggregateStatsAndBuildTodayFilters() {
            // 正常场景：六项统计全部装配，条件限定今日与 fail 状态
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            when(observabilityStatsMapper.countUserMessagesSince(any(LocalDateTime.class))).thenReturn(11L);
            when(observabilityStatsMapper.countAssistantMessagesSince(any(LocalDateTime.class))).thenReturn(22L);
            when(observabilityStatsMapper.avgOperationDuration()).thenReturn(3.5);
            when(operationLogService.count(any(Wrapper.class))).thenReturn(7L);
            when(traceRecordService.count(any(Wrapper.class))).thenReturn(9L);
            when(llmTraceStatsService.stats(7)).thenReturn(LlmTraceStatsDTO.builder()
                    .calls(100L).failures(4L)
                    .errorRate(new BigDecimal("0.0400"))
                    .cost(new BigDecimal("1.234567"))
                    .p95Duration(2800.0).p95Ttft(600.0)
                    .byModel(List.of(LlmTraceStatsDTO.ModelStat.builder()
                            .modelName("gpt-4o-mini").calls(100L).build()))
                    .build());

            R<Map<String, Object>> response = controller.dashboard(7);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData())
                    .containsEntry("todayChatCount", 11L)
                    .containsEntry("todayModelCallCount", 22L)
                    .containsEntry("todayErrorCount", 7L)
                    .containsEntry("avgDuration", 3.5)
                    .containsEntry("todayLogCount", 7L)
                    .containsEntry("todayTraceCount", 9L);

            // 对话/模型调用数的统计窗口是「今天 0 点」
            ArgumentCaptor<LocalDateTime> sinceCap = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(observabilityStatsMapper).countUserMessagesSince(sinceCap.capture());
            assertThat(sinceCap.getValue()).isEqualTo(todayStart);
            verify(observabilityStatsMapper).countAssistantMessagesSince(todayStart);

            // 错误数 = fail 且今日；第二次 count 只限今日
            ArgumentCaptor<Wrapper<OperationLogEntity>> logCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(operationLogService, times(2)).count(logCap.capture());
            LambdaQueryWrapper<?> errorWrapper = (LambdaQueryWrapper<?>) logCap.getAllValues().get(0);
            assertThat(errorWrapper.getTargetSql()).contains("status").contains("create_time");
            assertThat(errorWrapper.getParamNameValuePairs().values()).contains("fail", todayStart);

            LambdaQueryWrapper<?> todayLogWrapper = (LambdaQueryWrapper<?>) logCap.getAllValues().get(1);
            assertThat(todayLogWrapper.getTargetSql()).contains("create_time").doesNotContain("status");
            assertThat(todayLogWrapper.getParamNameValuePairs().values()).contains(todayStart);

            // 追踪数限定今日 start_time
            ArgumentCaptor<Wrapper<TraceRecordEntity>> traceCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(traceRecordService).count(traceCap.capture());
            LambdaQueryWrapper<?> traceWrapper = (LambdaQueryWrapper<?>) traceCap.getValue();
            assertThat(traceWrapper.getTargetSql()).contains("start_time");
            assertThat(traceWrapper.getParamNameValuePairs().values()).contains(todayStart);
        }

        @Test
        void shouldCarryWindowedLlmStatsAndDays() {
            // 正常场景：模型统计成组返回，窗口天数原样回传给前端展示「近 N 天」
            LlmTraceStatsDTO stats = LlmTraceStatsDTO.builder()
                    .calls(5L).tokens(800L).p95Duration(1500.0).byModel(List.of()).build();
            when(llmTraceStatsService.stats(30)).thenReturn(stats);

            R<Map<String, Object>> response = controller.dashboard(30);

            assertThat(response.getData())
                    .containsEntry("statsDays", 30)
                    .containsEntry("llmStats", stats);
            verify(llmTraceStatsService).stats(30);
        }

        @Test
        void shouldExposeTodayCountersEvenWhenNoLlmCallHappened() {
            // 边界场景：新环境一笔模型调用都没有，今日计数不能被一个空统计块顶掉
            when(llmTraceStatsService.stats(1)).thenReturn(LlmTraceStatsDTO.builder()
                    .calls(0L).errorRate(BigDecimal.ZERO).cost(BigDecimal.ZERO)
                    .byModel(List.of()).build());

            R<Map<String, Object>> response = controller.dashboard(1);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsKeys("todayChatCount", "todayTraceCount", "llmStats");
            LlmTraceStatsDTO stats = (LlmTraceStatsDTO) response.getData().get("llmStats");
            assertThat(stats.getCalls()).isZero();
            assertThat(stats.getP95Duration()).isNull();
        }
    }
}
