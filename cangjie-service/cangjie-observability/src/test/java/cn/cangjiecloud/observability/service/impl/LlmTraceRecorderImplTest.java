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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link LlmTraceRecorderImpl} 单元测试。
 * <p>
 * 覆盖元信息落库、traceId/sessionId/userId 解析优先级、消息序列化、
 * 内容脱敏/截断策略以及写入异常吞掉等核心逻辑，不连接真实数据库。
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
}
