package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.observability.ExportedSpan;
import cn.cangjiecloud.core.observability.TraceExporter;
import cn.cangjiecloud.core.observability.TraceSpan;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link TraceRecordServiceImpl} 单元测试。
 * <p>
 * 业务异步执行器替换为直接执行（{@code Runnable::run}）以便同步断言；
 * 覆盖 span 字段装配（spanId 约定、serviceName、startTime）、查询条件与外导出口，
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
        // 写入执行器换成同线程直跑，落库断言才不用等；生产用的是独立有界池
        ReflectionTestUtils.setField(service, "spanWriter", (Executor) Runnable::run);
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

        /**
         * 缓冲满时丢 span 并计数，而不是退回调用线程执行。
         * <p>
         * 一条 span 可能挂着数十 KB 正文，落到业务线程上同步写库等于把「不阻塞主链路」又请回来；
         * 但悄悄丢更糟 —— 得有一个数能让人知道丢过多少。
         */
        @Test
        void shouldCountDroppedSpanWhenWriterQueueRejected() {
            ReflectionTestUtils.setField(service, "spanWriter", (Executor) task -> {
                throw new RejectedExecutionException("queue full");
            });
            TraceRecordEntity entity = new TraceRecordEntity();
            entity.setTraceId("trace-1");

            assertThatCode(() -> service.record(entity)).doesNotThrowAnyException();

            assertThat(service.droppedSpanCount()).isEqualTo(1L);
            verify(service, never()).save(any(TraceRecordEntity.class));
        }

        /** 独立写入池用的是有界队列 + 拒绝即丢：不能借业务池的 CallerRunsPolicy */
        @Test
        void initWriterShouldBuildBoundedAbortPolicyPool() {
            TraceRecordServiceImpl fresh = new TraceRecordServiceImpl();
            ReflectionTestUtils.setField(fresh, "writeQueueCapacity", 7);

            ReflectionTestUtils.invokeMethod(fresh, "initWriter");
            Object writer = ReflectionTestUtils.getField(fresh, "spanWriter");

            assertThat(writer).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) writer;
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(7);
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            ReflectionTestUtils.invokeMethod(fresh, "shutdownWriter");
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

    // ==================== 带载荷的 span ====================

    @Nested
    class SpanWithPayload {

        @Test
        void shouldPersistInputOutputAndMetadata() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceSpan span = TraceSpan.builder()
                    .module("retrieval").action("search").traceId("trace-1")
                    .duration(37L).status("success").message("知识库检索: 2 条结果")
                    .input("{\"query\":\"什么是仓颉\"}")
                    .output("[{\"documentId\":\"doc1\"}]")
                    .metadata(Map.of("hitCount", 2, "topScore", 0.91))
                    .build();

            service.record(span);

            TraceRecordEntity saved = captureSavedSpan();
            assertThat(saved.getInputPayload()).isEqualTo("{\"query\":\"什么是仓颉\"}");
            assertThat(saved.getOutputPayload()).isEqualTo("[{\"documentId\":\"doc1\"}]");
            // metadata 是 Map，落库前序列化成 JSON 文本
            assertThat(JsonUtils.parseMap(saved.getMetadata()))
                    .containsEntry("hitCount", 2).containsEntry("topScore", 0.91);
            assertThat(saved.getSpanId()).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(saved.getParentSpanId()).isEqualTo("trace-1");
        }

        @Test
        void shouldKeepSpanWhenPayloadAbsent() {
            service.record(TraceSpan.of("retrieval", "search", "trace-1", 5L, "success", null));

            TraceRecordEntity saved = captureSavedSpan();
            assertThat(saved.getInputPayload()).isNull();
            assertThat(saved.getOutputPayload()).isNull();
            assertThat(saved.getMetadata()).isNull();
        }

        @Test
        void shouldDropUnserializableMetadataButKeepSpan() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceSpan span = TraceSpan.builder()
                    .module("retrieval").action("search").traceId("trace-1")
                    .duration(5L).status("success")
                    // 空 bean 触发 Jackson FAIL_ON_EMPTY_BEANS，正文丢住至少还留着耗时
                    .metadata(Map.of("broken", new Object() {
                    }))
                    .build();

            assertThatCode(() -> service.record(span)).doesNotThrowAnyException();

            TraceRecordEntity saved = captureSavedSpan();
            assertThat(saved.getMetadata()).isNull();
            assertThat(saved.getDuration()).isEqualTo(5L);
        }

        @Test
        void shouldIgnoreNullSpan() {
            service.record((TraceSpan) null);

            verify(service, never()).save(any(TraceRecordEntity.class));
        }
    }

    // ==================== payload ====================

    @Nested
    class SpanPayload {

        @Test
        void shouldReturnNullWithoutQueryWhenSpanIdBlank() {
            assertThat(service.payload(null)).isNull();
            assertThat(service.payload("  ")).isNull();

            verify(service, never()).getOne(any(Wrapper.class));
        }

        @Test
        void shouldQuerySingleSpanBySpanId() {
            TraceRecordEntity record = new TraceRecordEntity();
            record.setSpanId("span-1");
            record.setInputPayload("{\"query\":\"q\"}");
            doReturn(record).when(service).getOne(any(Wrapper.class));

            assertThat(service.payload("span-1")).isSameAs(record);

            ArgumentCaptor<Wrapper<TraceRecordEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(wrapperCaptor.capture());
            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("span_id");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("span-1");
        }
    }

    // ==================== span 外导 ====================

    /**
     * 出口挂在落库点上：实体必须在调用线程组装完整（拿得到 startTime），
     * 而单个出口抛异常不能把其他出口与写入一起带走。
     */
    @Nested
    class TraceExport {

        private TraceRecordEntity entity() {
            TraceRecordEntity entity = new TraceRecordEntity();
            entity.setTraceId("trace-1");
            entity.setSpanId("span-1");
            entity.setParentSpanId("trace-1");
            entity.setServiceName("cangjie-agent");
            entity.setModule("retrieval");
            entity.setAction("search");
            entity.setStatus("success");
            entity.setMessage("2 条结果");
            entity.setStartTime(LocalDateTime.of(2026, 10, 12, 10, 30));
            entity.setDuration(37L);
            entity.setInputPayload("{\"query\":\"q\"}");
            entity.setOutputPayload("[{\"documentId\":\"doc1\"}]");
            entity.setMetadata("{\"hitCount\":2}");
            return entity;
        }

        @Test
        void shouldExportSpanAssembledFromEntity() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceExporter exporter = mock(TraceExporter.class);
            ReflectionTestUtils.setField(service, "traceExporters", List.of(exporter));

            service.record(entity());

            ArgumentCaptor<ExportedSpan> captor = ArgumentCaptor.forClass(ExportedSpan.class);
            verify(exporter).export(captor.capture());
            ExportedSpan span = captor.getValue();
            assertThat(span.traceId()).isEqualTo("trace-1");
            assertThat(span.spanId()).isEqualTo("span-1");
            assertThat(span.parentSpanId()).isEqualTo("trace-1");
            assertThat(span.serviceName()).isEqualTo("cangjie-agent");
            assertThat(span.module()).isEqualTo("retrieval");
            assertThat(span.action()).isEqualTo("search");
            assertThat(span.status()).isEqualTo("success");
            assertThat(span.message()).isEqualTo("2 条结果");
            assertThat(span.durationMs()).isEqualTo(37L);
            // LocalDateTime 必须翻成绝对时间，否则出口按本地时区换一次就把直方图抬位
            assertThat(span.startEpochMs()).isEqualTo(
                    LocalDateTime.of(2026, 10, 12, 10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            assertThat(span.endEpochMs()).isEqualTo(span.startEpochMs() + 37L);
            assertThat(span.input()).isEqualTo("{\"query\":\"q\"}");
            assertThat(span.output()).isEqualTo("[{\"documentId\":\"doc1\"}]");
            assertThat(span.metadata()).containsEntry("hitCount", 2);
            assertThat(span.failed()).isFalse();
        }

        @Test
        void shouldDeliverToEveryExporterAndIsolateOneFailure() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceExporter broken = mock(TraceExporter.class);
            TraceExporter healthy = mock(TraceExporter.class);
            doThrow(new RuntimeException("collector down")).when(broken).export(any(ExportedSpan.class));
            ReflectionTestUtils.setField(service, "traceExporters", List.of(broken, healthy));

            assertThatCode(() -> service.record(entity())).doesNotThrowAnyException();

            // 一个出口抖动不能把另一出口和落库一起拖下
            verify(healthy).export(any(ExportedSpan.class));
            verify(service).save(any(TraceRecordEntity.class));
        }

        @Test
        void shouldExportBeforeSaveSoDatabaseFailureKeepsExternalCopy() {
            doThrow(new RuntimeException("db down")).when(service).save(any(TraceRecordEntity.class));
            TraceExporter exporter = mock(TraceExporter.class);
            ReflectionTestUtils.setField(service, "traceExporters", List.of(exporter));

            service.record(entity());

            verify(exporter).export(any(ExportedSpan.class));
        }

        @Test
        void shouldStillExportWhenParentAndMetadataAbsent() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));
            TraceExporter exporter = mock(TraceExporter.class);
            ReflectionTestUtils.setField(service, "traceExporters", List.of(exporter));
            TraceRecordEntity entry = entity();
            entry.setParentSpanId(null);
            // 落库列里已有的脏数据不能让出口整条看不到
            entry.setMetadata("not-json");
            entry.setDuration(null);
            entry.setStartTime(null);

            service.record(entry);

            ArgumentCaptor<ExportedSpan> captor = ArgumentCaptor.forClass(ExportedSpan.class);
            verify(exporter).export(captor.capture());
            ExportedSpan span = captor.getValue();
            assertThat(span.parentSpanId()).isNull();
            assertThat(span.metadata()).isNull();
            assertThat(span.durationMs()).isZero();
            assertThat(span.startEpochMs()).isPositive();
            assertThat(span.failed()).isFalse();
        }

        @Test
        void shouldRecordNormallyWithoutExporters() {
            doReturn(true).when(service).save(any(TraceRecordEntity.class));

            assertThatCode(() -> service.record(entity())).doesNotThrowAnyException();

            verify(service).save(any(TraceRecordEntity.class));
        }

        @Test
        void shouldNotExportSkippedEntity() {
            TraceExporter exporter = mock(TraceExporter.class);
            ReflectionTestUtils.setField(service, "traceExporters", List.of(exporter));

            service.record((TraceRecordEntity) null);

            verify(exporter, never()).export(any(ExportedSpan.class));
        }
    }
}
