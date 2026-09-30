package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.TraceQueryDTO;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link TraceRecordServiceImpl} 单元测试。
 * <p>
 * 业务异步执行器替换为直接执行（{@code Runnable::run}）以便同步断言；
 * 覆盖 span 字段装配（spanId 约定、serviceName、startTime）与查询条件，
 * 通过 spy 拦截继承的 save/page 方法，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class TraceRecordServiceImplTest {

    @Mock
    private TraceRecordMapper traceRecordMapper;

    private TraceRecordServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(TraceRecordMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TraceRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new TraceRecordServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", traceRecordMapper);
        ReflectionTestUtils.setField(service, "businessExecutor", (Executor) Runnable::run);
    }

    private TraceRecordEntity captureSavedSpan() {
        ArgumentCaptor<TraceRecordEntity> captor = ArgumentCaptor.forClass(TraceRecordEntity.class);
        verify(service).save(captor.capture());
        return captor.getValue();
    }

    // ==================== record ====================

    @Nested
    class RecordEntity {

        @Test
        void shouldSkipNullEntity() {
            service.record((TraceRecordEntity) null);

            verify(service, never()).save(any(TraceRecordEntity.class));
        }

        @Test
        void shouldSaveEntityAsynchronously() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceRecordEntity entity = new TraceRecordEntity();
            entity.setTraceId("trace-1");

            service.record(entity);

            verify(service).save(entity);
        }

        @Test
        void shouldSwallowSaveFailureSoBusinessIsNotBroken() {
            doThrow(new RuntimeException("db down")).when(service).save(any(TraceRecordEntity.class));

            assertThatCode(() -> service.record(new TraceRecordEntity())).doesNotThrowAnyException();
        }
    }

    // ==================== span 构建 ====================

    @Nested
    class SpanBuilding {

        @Test
        void shouldBuildChildSpanWithGeneratedSpanIdAndParentTraceId() {
            service.record("order", "create", "trace-1", 120L, "success", "ok");

            TraceRecordEntity span = captureSavedSpan();
            assertThat(span.getTraceId()).isEqualTo("trace-1");
            assertThat(span.getModule()).isEqualTo("order");
            assertThat(span.getAction()).isEqualTo("create");
            assertThat(span.getDuration()).isEqualTo(120L);
            assertThat(span.getStatus()).isEqualTo("success");
            assertThat(span.getMessage()).isEqualTo("ok");
            assertThat(span.getServiceName()).isEqualTo("cangjie-agent");
            assertThat(span.getSpanId()).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(span.getParentSpanId()).isEqualTo("trace-1");
            assertThat(span.getStartTime())
                    .isBetween(LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        }

        @Test
        void shouldUseTraceIdAsEntrySpanId() {
            service.recordEntry("chat", "send", "trace-1", 88L, "fail", "boom");

            TraceRecordEntity span = captureSavedSpan();
            assertThat(span.getSpanId()).isEqualTo("trace-1");
            assertThat(span.getParentSpanId()).isNull();
            assertThat(span.getModule()).isEqualTo("chat");
            assertThat(span.getAction()).isEqualTo("send");
            assertThat(span.getStatus()).isEqualTo("fail");
            assertThat(span.getMessage()).isEqualTo("boom");
            assertThat(span.getServiceName()).isEqualTo("cangjie-agent");
        }

        @Test
        void shouldGenerateEntrySpanIdWhenTraceIdBlank() {
            service.recordEntry("chat", "send", " ", 1L, "success", null);

            TraceRecordEntity span = captureSavedSpan();
            assertThat(span.getSpanId()).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(span.getParentSpanId()).isNull();
        }
    }

    // ==================== pageQuery ====================

    @Nested
    class PageQuery {

        @Test
        void shouldApplyFiltersAndOrderByStartTimeDesc() {
            doReturn(new Page<>(3, 50)).when(service).page(any(IPage.class), any(Wrapper.class));
            TraceQueryDTO query = new TraceQueryDTO();
            query.setTraceId("trace-1");
            query.setModule("order");
            query.setAction("create");
            query.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
            query.setEndTime(LocalDateTime.of(2026, 2, 1, 0, 0));
            query.setPageNum(3);
            query.setPageSize(50);

            IPage<TraceRecordEntity> page = service.pageQuery(query);

            assertThat(page.getCurrent()).isEqualTo(3);
            assertThat(page.getSize()).isEqualTo(50);

            ArgumentCaptor<IPage<TraceRecordEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<TraceRecordEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            LambdaQueryWrapper<TraceRecordEntity> wrapper =
                    (LambdaQueryWrapper<TraceRecordEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("trace_id", "module", "action", "start_time");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("trace-1", "order", "create");
        }

        @Test
        void shouldUsePagingDefaultsAndSkipBlankFilters() {
            doReturn(new Page<>(1, 10)).when(service).page(any(IPage.class), any(Wrapper.class));
            TraceQueryDTO query = new TraceQueryDTO();
            query.setTraceId(" ");
            query.setPageNum(null);
            query.setPageSize(null);

            service.pageQuery(query);

            ArgumentCaptor<IPage<TraceRecordEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<TraceRecordEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
            assertThat(((LambdaQueryWrapper<TraceRecordEntity>) wrapperCaptor.getValue()).getTargetSql())
                    .doesNotContain("trace_id", "module", "action");
        }
    }
}
