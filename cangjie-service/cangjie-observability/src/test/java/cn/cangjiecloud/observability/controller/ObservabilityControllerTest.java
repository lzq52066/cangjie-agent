package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.dto.LlmTraceStatsDTO;
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
 * {@link ObservabilityController} 单元测试：验证日志/指标查询的参数透传、
 * dashboard 的统计聚合与条件拼接。
 */
@ExtendWith(MockitoExtension.class)
class ObservabilityControllerTest {

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
