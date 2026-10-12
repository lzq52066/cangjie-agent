package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link LlmTraceRecorderImpl} 单元测试。
 * <p>
 * 覆盖元信息落库、traceId/sessionId/userId 解析优先级、消息序列化、
 * 内容脱敏/截断策略、写入异常吞掉，以及异步批量写与采样，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class LlmTraceRecorderImplTest {

    @Mock
    private ILlmTraceService llmTraceService;

    private LlmTraceRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        recorder = new LlmTraceRecorderImpl(llmTraceService);
        ReflectionTestUtils.setField(recorder, "maskEnabled", false);
        ReflectionTestUtils.setField(recorder, "storeContent", true);
        ReflectionTestUtils.setField(recorder, "maxContentLength", 20000);
        // 默认走同步逐条写：异步与采样单独在 AsyncAndSampling 里验
        ReflectionTestUtils.setField(recorder, "asyncEnabled", false);
        ReflectionTestUtils.setField(recorder, "sampleRate", 1.0);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private LlmTraceEntity captureTrace() {
        ArgumentCaptor<LlmTraceEntity> captor = ArgumentCaptor.forClass(LlmTraceEntity.class);
        verify(llmTraceService).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    class RecordSuccess {

        @Test
        void shouldPersistAllMetadataFromRecord() {
            long startMs = Instant.parse("2026-03-04T05:06:07Z").toEpochMilli();

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .traceId("trace-1")
                    .runId("run-1")
                    .requestId("req-1")
                    .appId("app-1")
                    .appName("demo-app")
                    .sessionId("session-1")
                    .userId("user-1")
                    .modelId("model-1")
                    .modelName("gpt-4o")
                    .promptContent("hello prompt")
                    .inputTokens(11L)
                    .outputTokens(22L)
                    .totalTokens(33L)
                    .responseContent("hello response")
                    .finishReason("stop")
                    .duration(1234L)
                    .startTimeMs(startMs)
                    .build());

            LlmTraceEntity trace = captureTrace();
            assertThat(trace.getTraceId()).isEqualTo("trace-1");
            assertThat(trace.getRunId()).isEqualTo("run-1");
            assertThat(trace.getRequestId()).isEqualTo("req-1");
            assertThat(trace.getAppId()).isEqualTo("app-1");
            assertThat(trace.getAppName()).isEqualTo("demo-app");
            assertThat(trace.getSessionId()).isEqualTo("session-1");
            assertThat(trace.getUserId()).isEqualTo("user-1");
            assertThat(trace.getModelId()).isEqualTo("model-1");
            assertThat(trace.getModelName()).isEqualTo("gpt-4o");
            assertThat(trace.getPromptContent()).isEqualTo("hello prompt");
            assertThat(trace.getResponseContent()).isEqualTo("hello response");
            assertThat(trace.getInputTokens()).isEqualTo(11L);
            assertThat(trace.getOutputTokens()).isEqualTo(22L);
            assertThat(trace.getTotalTokens()).isEqualTo(33L);
            assertThat(trace.getFinishReason()).isEqualTo("stop");
            assertThat(trace.getDuration()).isEqualTo(1234L);
            assertThat(trace.getStatus()).isEqualTo("success");
            assertThat(trace.getErrorMessage()).isNull();
            assertThat(trace.getStartTime()).isEqualTo(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()));
        }

        @Test
        void shouldFallbackToTraceContextWhenMetadataMissing() {
            TraceContext.setTraceId("ctx-trace");
            TraceContext.setSessionId("ctx-session");
            TraceContext.setUserId("ctx-user");

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder().build());

            LlmTraceEntity trace = captureTrace();
            assertThat(trace.getTraceId()).isEqualTo("ctx-trace");
            assertThat(trace.getSessionId()).isEqualTo("ctx-session");
            assertThat(trace.getUserId()).isEqualTo("ctx-user");
            assertThat(trace.getStartTime()).isNotNull();
        }

        @Test
        void shouldSerializeMessagesWhenPromptContentAbsent() {
            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .messages(List.of(UserMessage.from("hi langchain4j")))
                    .build());

            assertThat(captureTrace().getPromptContent()).contains("hi langchain4j");
        }

        @Test
        void shouldPreferExplicitPromptContentOverMessages() {
            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent("explicit")
                    .messages(List.of(UserMessage.from("from-messages")))
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo("explicit");
        }
    }

    @Nested
    class RecordFailure {

        @Test
        void shouldPersistFailStatusAndErrorMessage() {
            recorder.recordFailure(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .traceId("trace-1")
                    .responseContent(null)
                    .build(), "超时");

            LlmTraceEntity trace = captureTrace();
            assertThat(trace.getStatus()).isEqualTo("fail");
            assertThat(trace.getErrorMessage()).isEqualTo("超时");
        }

        @Test
        void shouldSwallowPersistFailure() {
            doThrow(new RuntimeException("db down")).when(llmTraceService).save(any(LlmTraceEntity.class));

            assertThatCode(() -> recorder.recordSuccess(
                    ILlmTraceRecorder.LlmTraceRecord.builder().build())).doesNotThrowAnyException();
        }

        @Test
        void shouldDropPromptButKeepMetadataWhenSerializationFails() {
            @SuppressWarnings("unchecked")
            ChatMessage broken = (ChatMessage) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{ChatMessage.class},
                    (proxy, method, args) -> {
                        throw new IllegalStateException("cannot serialize");
                    });

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .traceId("trace-1")
                    .messages(List.of(broken))
                    .build());

            LlmTraceEntity trace = captureTrace();
            assertThat(trace.getTraceId()).isEqualTo("trace-1");
            assertThat(trace.getPromptContent()).isNull();
        }
    }

    @Nested
    class ContentPolicy {

        @Test
        void shouldKeepOnlyHeadWhenStoreContentDisabled() {
            ReflectionTestUtils.setField(recorder, "storeContent", false);
            String head = "a".repeat(200);
            String content = head + "b".repeat(50);

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent(content)
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo(head + "...[truncated]");
        }

        @Test
        void shouldKeepShortContentIntactWhenStoreContentDisabled() {
            ReflectionTestUtils.setField(recorder, "storeContent", false);

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent("short")
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo("short");
        }

        @Test
        void shouldTruncateWhenExceedingMaxContentLength() {
            ReflectionTestUtils.setField(recorder, "maxContentLength", 10);

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent("0123456789ABCDEF")
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo("0123456789...[truncated]");
        }

        @Test
        void shouldNotTruncateWhenMaxContentLengthNotPositive() {
            ReflectionTestUtils.setField(recorder, "maxContentLength", 0);
            String content = "x".repeat(30_000);

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent(content)
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo(content);
        }

        @Test
        void shouldMaskSensitiveFieldsWhenEnabled() {
            ReflectionTestUtils.setField(recorder, "maskEnabled", true);
            String json = "{\"apiKey\":\"sk-123\",\"password\":\"p@ss\",\"token\":\"tok\","
                    + "\"authorization\":\"Bearer abc\"}";
            String content = json + " phone=13800138000 id=110101199003077758";

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent(content)
                    .build());

            String stored = captureTrace().getPromptContent();
            assertThat(stored).doesNotContain("sk-123", "p@ss", "Bearer abc",
                    "13800138000", "110101199003077758");
            assertThat(stored).contains("\"apiKey\":\"******\"", "\"password\":\"******\"",
                    "\"token\":\"******\"", "\"authorization\":\"******\"");
        }

        @Test
        void shouldNotMaskWhenDisabled() {
            String content = "{\"apiKey\":\"sk-123\"}";

            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .promptContent(content)
                    .build());

            assertThat(captureTrace().getPromptContent()).isEqualTo(content);
        }

        @Test
        void shouldLeaveNullContentAsNull() {
            recorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .traceId("trace-1")
                    .build());

            LlmTraceEntity trace = captureTrace();
            assertThat(trace.getPromptContent()).isNull();
            assertThat(trace.getResponseContent()).isNull();
        }
    }

    /**
     * 异步批量写与采样。
     * <p>
     * 这组的重点是「对话线程不等库」与「采样不能把错误采掉」：
     * record 时只入队，真正写库发生在 flush 那一下。
     */
    @Nested
    class AsyncAndSampling {

        private LlmTraceRecorderImpl asyncRecorder(int queueCapacity, int batchSize) {
            LlmTraceRecorderImpl instance = new LlmTraceRecorderImpl(llmTraceService);
            ReflectionTestUtils.setField(instance, "storeContent", true);
            ReflectionTestUtils.setField(instance, "maxContentLength", 0);
            ReflectionTestUtils.setField(instance, "sampleRate", 1.0);
            ReflectionTestUtils.setField(instance, "asyncEnabled", true);
            ReflectionTestUtils.setField(instance, "queueCapacity", queueCapacity);
            ReflectionTestUtils.setField(instance, "batchSize", batchSize);
            // 不起定时线程：间隔给到极大，本组自己控排空时机
            ReflectionTestUtils.setField(instance, "flushIntervalMs", Long.MAX_VALUE);
            instance.init();
            return instance;
        }

        private static ILlmTraceRecorder.LlmTraceRecord record(String traceId) {
            return ILlmTraceRecorder.LlmTraceRecord.builder().traceId(traceId).build();
        }

        /** 记录时不写库，flush 时才批量写：一次对话 2~5 条调用不该各占一次 DB 往返 */
        @Test
        void shouldBufferAndWriteInBatch() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 10);

            for (int i = 0; i < 5; i++) {
                async.recordSuccess(record("t" + i));
            }

            verify(llmTraceService, never()).save(any(LlmTraceEntity.class));
            verify(llmTraceService, never()).saveBatch(anyList());
            assertThat(async.queuedSize()).isEqualTo(5);

            async.destroy();

            ArgumentCaptor<List<LlmTraceEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(llmTraceService).saveBatch(captor.capture());
            assertThat(captor.getValue()).hasSize(5);
            assertThat(captor.getValue()).extracting(LlmTraceEntity::getTraceId)
                    .containsExactly("t0", "t1", "t2", "t3", "t4");
        }

        /** 攒多了分批写，不把一整段缓冲拼成一条巨型 SQL */
        @Test
        void shouldSplitIntoMultipleBatches() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 2);
            for (int i = 0; i < 5; i++) {
                async.recordSuccess(record("t" + i));
            }

            async.destroy();

            verify(llmTraceService, times(3)).saveBatch(anyList());
        }

        /** 队列满了丢新来的并计数：可观测性数据不值得把对话主线程拖住等库 */
        @Test
        void shouldDropNewestWhenQueueSaturated() {
            LlmTraceRecorderImpl async = asyncRecorder(1, 10);

            async.recordSuccess(record("t0"));
            async.recordSuccess(record("t1"));
            async.recordSuccess(record("t2"));

            assertThat(async.queuedSize()).isEqualTo(1);
            assertThat(async.droppedCount()).isEqualTo(2);
            assertThatCode(() -> async.destroy()).doesNotThrowAnyException();
        }

        /**
         * 写失败不接着挖队列，但这一批要能等下一轮：一次库抖动作把缓冲整段丢掉，
         * 等于「库坏了三分钟，那三分钟的调用记录全没了」，而采样率与容量看起来都正常。
         */
        @Test
        void shouldStopDrainingWhenBatchWriteFails() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 1);
            doThrow(new RuntimeException("db down")).when(llmTraceService).saveBatch(anyList());
            async.recordSuccess(record("t0"));
            async.recordSuccess(record("t1"));

            async.destroy();

            // 失败按批计一次，两条都退回缓冲原地等下一轮，一条没丢
            assertThat(async.writeFailureCount()).isEqualTo(1);
            assertThat(async.queuedSize()).isEqualTo(2);
            assertThat(async.droppedCount()).isZero();
        }

        /** 退不回来（缓冲已被新数据占满）的那些才计入丢弃，别让丢数静默发生 */
        @Test
        void shouldCountLostRowsWhenRequeueDoesNotFit() {
            LlmTraceRecorderImpl async = asyncRecorder(1, 10);
            doThrow(new RuntimeException("db down")).when(llmTraceService).saveBatch(anyList());
            async.recordSuccess(record("t0"));
            async.recordSuccess(record("t1"));
            async.recordSuccess(record("t2"));

            async.destroy();

            assertThat(async.droppedCount()).isPositive();
            assertThat(async.writeFailureCount()).isPositive();
        }

        /** 采样只剔成功调用：失败永远留档，否则可观测性就只剩下「一切正常」 */
        @Test
        void shouldSampleSuccessButKeepEveryFailure() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 10);
            ReflectionTestUtils.setField(async, "sampleRate", 0.0);

            async.recordSuccess(record("ok-1"));
            async.recordSuccess(record("ok-2"));
            async.recordFailure(record("bad-1"), "超时");

            assertThat(async.queuedSize()).isEqualTo(1);

            async.destroy();

            ArgumentCaptor<List<LlmTraceEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(llmTraceService).saveBatch(captor.capture());
            assertThat(captor.getValue()).singleElement()
                    .satisfies(trace -> {
                        assertThat(trace.getTraceId()).isEqualTo("bad-1");
                        assertThat(trace.getStatus()).isEqualTo("fail");
                    });
        }

        /**
         * 采样按 traceId 决定，而不是按条掷骰子：一次请求的 2~5 条模型调用要么整条链路留下，
         * 要么整条链路不留 —— 逐条随机会留下「只有第二次调用、没有第一次」的残片，
         * 链路视图与耗时/Token 汇总都不再可信。
         */
        @Test
        void samplingShouldBeStickyWithinOneTraceId() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 10);
            ReflectionTestUtils.setField(async, "sampleRate", 0.5);

            for (int i = 0; i < 5; i++) {
                async.recordSuccess(record("same-trace"));
            }

            // 同 traceId 只会是「全收」或「全丢」两种结果
            assertThat(async.queuedSize()).isIn(0, 5);
        }

        /** 同一采样率下不同 traceId 仍要散布开，否则抽样失去代表性 */
        @Test
        void samplingShouldSpreadAcrossTraceIds() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 10);
            ReflectionTestUtils.setField(async, "sampleRate", 0.5);

            for (int i = 0; i < 40; i++) {
                async.recordSuccess(record("trace-" + i));
            }

            assertThat(async.queuedSize()).isBetween(5, 35);
        }

        /** 采样率 1 全留，不因为多一层随机而丢数据 */
        @Test
        void shouldKeepEverySuccessWhenRateIsOne() {
            LlmTraceRecorderImpl async = asyncRecorder(100, 10);

            for (int i = 0; i < 20; i++) {
                async.recordSuccess(record("t" + i));
            }

            assertThat(async.queuedSize()).isEqualTo(20);
            assertThat(async.droppedCount()).isZero();
        }

        /** 关掉异步开关时逐条同步写，与拆队之前同行为 */
        @Test
        void shouldWriteSynchronouslyWhenAsyncDisabled() {
            recorder.recordSuccess(record("t0"));

            verify(llmTraceService).save(any(LlmTraceEntity.class));
            verify(llmTraceService, never()).saveBatch(anyList());
        }
    }
}
