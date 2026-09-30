package cn.cangjiecloud.observability.listener;

import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmTraceChatModelListener} 单元测试。
 * <p>
 * 覆盖 onRequest 落开始时间戳、onResponse/onError 组装 LlmTraceRecord 的字段来源优先级
 * （业务上下文优先、缺省回填厂商响应）、耗时与 token 归集，以及「可观测性失败绝不影响主流程」的兜底。
 */
@ExtendWith(MockitoExtension.class)
class LlmTraceChatModelListenerTest {

    /** 与实现约定的开始时间戳键名 */
    private static final String KEY_START_MS = "cangjie.listener.startMs";

    @Mock
    private ILlmTraceRecorder llmTraceRecorder;

    private LlmTraceChatModelListener listener;

    @BeforeEach
    void setUp() {
        listener = new LlmTraceChatModelListener(llmTraceRecorder);
    }

    @Nested
    class OnRequest {

        @Test
        void shouldStampStartTimeIntoListenerAttributes() {
            Map<Object, Object> attributes = new LinkedHashMap<>();
            ChatModelRequestContext context = new ChatModelRequestContext(
                    ChatRequest.builder().messages(UserMessage.from("你好")).build(),
                    ModelProvider.OPEN_AI,
                    attributes);
            long before = System.currentTimeMillis();

            listener.onRequest(context);

            long after = System.currentTimeMillis();
            assertThat(attributes.get(KEY_START_MS)).isInstanceOf(Long.class);
            assertThat((Long) attributes.get(KEY_START_MS)).isBetween(before, after);
        }
    }

    @Nested
    class OnResponse {

        @Test
        void shouldRecordSuccessWithBusinessContextAndUsage() {
            Map<Object, Object> attributes = traceAttributes();
            long startMs = System.currentTimeMillis() - 1500;
            attributes.put(KEY_START_MS, startMs);
            List<ChatMessage> messages = List.of(UserMessage.from("你好"));
            ChatRequest request = ChatRequest.builder().messages(messages).build();
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(AiMessage.from("答复正文"))
                    .id("resp-1")
                    .modelName("gpt-4o")
                    .tokenUsage(new TokenUsage(10, 20, 30))
                    .finishReason(FinishReason.STOP)
                    .build();

            listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OPEN_AI, attributes));

            ILlmTraceRecorder.LlmTraceRecord record = captureSuccess();
            assertThat(record.getTraceId()).isEqualTo("trace-1");
            assertThat(record.getRunId()).isEqualTo("run-1");
            assertThat(record.getAppId()).isEqualTo("app-1");
            assertThat(record.getAppName()).isEqualTo("演示应用");
            assertThat(record.getSessionId()).isEqualTo("session-1");
            assertThat(record.getUserId()).isEqualTo("user-1");
            assertThat(record.getModelId()).isEqualTo("model-id-1");
            assertThat(record.getRequestId()).isEqualTo("resp-1");
            assertThat(record.getModelName()).isEqualTo("gpt-4o");
            assertThat(record.getMessages()).isEqualTo(messages);
            assertThat(record.getResponseContent()).isEqualTo("答复正文");
            assertThat(record.getFinishReason()).isEqualTo("STOP");
            assertThat(record.getInputTokens()).isEqualTo(10L);
            assertThat(record.getOutputTokens()).isEqualTo(20L);
            assertThat(record.getTotalTokens()).isEqualTo(30L);
            assertThat(record.getStartTimeMs()).isEqualTo(startMs);
            assertThat(record.getDuration()).isGreaterThanOrEqualTo(1500L);
        }

        @Test
        void shouldPreferBusinessRequestIdAndModelNameOverVendorValues() {
            Map<Object, Object> attributes = traceAttributes();
            attributes.put(ChatTraceContext.KEY_REQUEST_ID, "req-biz");
            attributes.put(ChatTraceContext.KEY_MODEL_NAME, "自定义显示名");
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .id("resp-vendor")
                    .modelName("gpt-4o")
                    .build();

            listener.onResponse(new ChatModelResponseContext(
                    response, chatRequest(), ModelProvider.OPEN_AI, attributes));

            ILlmTraceRecorder.LlmTraceRecord record = captureSuccess();
            assertThat(record.getRequestId()).isEqualTo("req-biz");
            assertThat(record.getModelName()).isEqualTo("自定义显示名");
        }

        @Test
        void shouldFallbackToCurrentTimeWhenStartMarkerMissing() {
            long before = System.currentTimeMillis();

            listener.onResponse(new ChatModelResponseContext(
                    ChatResponse.builder().aiMessage(AiMessage.from("ok")).build(),
                    chatRequest(),
                    ModelProvider.OPEN_AI,
                    traceAttributes()));

            ILlmTraceRecorder.LlmTraceRecord record = captureSuccess();
            assertThat(record.getStartTimeMs()).isGreaterThanOrEqualTo(before);
            assertThat(record.getDuration()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        void shouldLeaveOptionalFieldsNullWhenAbsent() {
            // 无业务上下文、无 token 用量、无完成原因：字段允许为空，不应抛异常
            // ChatResponse 构造器强制 aiMessage 非空，这里用 mock 覆盖"厂商响应字段全缺省"的极端形态
            ChatResponse bareResponse = mock(ChatResponse.class);
            listener.onResponse(new ChatModelResponseContext(
                    bareResponse, chatRequest(), ModelProvider.OPEN_AI, new LinkedHashMap<>()));

            ILlmTraceRecorder.LlmTraceRecord record = captureSuccess();
            assertThat(record.getTraceId()).isNull();
            assertThat(record.getRequestId()).isNull();
            assertThat(record.getModelName()).isNull();
            assertThat(record.getMessages()).hasSize(1);
            assertThat(record.getResponseContent()).isNull();
            assertThat(record.getFinishReason()).isNull();
            assertThat(record.getInputTokens()).isNull();
            assertThat(record.getOutputTokens()).isNull();
            assertThat(record.getTotalTokens()).isNull();
        }

        @Test
        void shouldSwallowRecorderFailure() {
            doThrow(new RuntimeException("db down")).when(llmTraceRecorder).recordSuccess(any());

            assertThatCode(() -> listener.onResponse(new ChatModelResponseContext(
                    ChatResponse.builder().aiMessage(AiMessage.from("ok")).build(),
                    chatRequest(),
                    ModelProvider.OPEN_AI,
                    traceAttributes()))).doesNotThrowAnyException();
        }
    }

    @Nested
    class OnError {

        @Test
        void shouldRecordFailureWithMappedErrorMessage() {
            Map<Object, Object> attributes = traceAttributes();
            long startMs = System.currentTimeMillis() - 800;
            attributes.put(KEY_START_MS, startMs);
            List<ChatMessage> messages = List.of(UserMessage.from("你好"));
            Throwable error = new RuntimeException("Connection refused");

            listener.onError(new ChatModelErrorContext(
                    error, ChatRequest.builder().messages(messages).build(), ModelProvider.OPEN_AI, attributes));

            ArgumentCaptor<ILlmTraceRecorder.LlmTraceRecord> recordCaptor =
                    ArgumentCaptor.forClass(ILlmTraceRecorder.LlmTraceRecord.class);
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmTraceRecorder).recordFailure(recordCaptor.capture(), messageCaptor.capture());
            assertThat(messageCaptor.getValue())
                    .isEqualTo("模型连接失败，请检查网络和模型地址配置（Connection refused）");

            ILlmTraceRecorder.LlmTraceRecord record = recordCaptor.getValue();
            assertThat(record.getTraceId()).isEqualTo("trace-1");
            assertThat(record.getRunId()).isEqualTo("run-1");
            assertThat(record.getMessages()).isEqualTo(messages);
            assertThat(record.getResponseContent()).isNull();
            assertThat(record.getFinishReason()).isNull();
            assertThat(record.getStartTimeMs()).isEqualTo(startMs);
            assertThat(record.getDuration()).isGreaterThanOrEqualTo(800L);
        }

        @Test
        void shouldNotRecordErrorFinishReasonButLeaveVendorFieldsEmpty() {
            listener.onError(new ChatModelErrorContext(
                    new RuntimeException("boom"),
                    chatRequest(),
                    ModelProvider.OPEN_AI,
                    new LinkedHashMap<>()));

            ILlmTraceRecorder.LlmTraceRecord record = captureFailure();
            assertThat(record.getTraceId()).isNull();
            assertThat(record.getRequestId()).isNull();
            assertThat(record.getModelName()).isNull();
        }

        @Test
        void shouldTolerateNullErrorThrowable() {
            // ChatModelErrorContext 构造器拒绝 null error，用 mock 模拟上游传入空异常的极端形态
            Map<Object, Object> attributes = new LinkedHashMap<>();
            ChatModelErrorContext context = mock(ChatModelErrorContext.class);
            when(context.error()).thenReturn(null);
            when(context.chatRequest()).thenReturn(chatRequest());
            when(context.attributes()).thenReturn(attributes);

            listener.onError(context);

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmTraceRecorder).recordFailure(any(), messageCaptor.capture());
            assertThat(messageCaptor.getValue()).isEqualTo("未知错误");
        }

        @Test
        void shouldSwallowRecorderFailure() {
            doThrow(new RuntimeException("db down")).when(llmTraceRecorder).recordFailure(any(), anyString());

            assertThatCode(() -> listener.onError(new ChatModelErrorContext(
                    new RuntimeException("boom"),
                    chatRequest(),
                    ModelProvider.OPEN_AI,
                    traceAttributes()))).doesNotThrowAnyException();
        }
    }

    private ChatRequest chatRequest() {
        return ChatRequest.builder().messages(UserMessage.from("你好")).build();
    }

    private Map<Object, Object> traceAttributes() {
        return ChatTraceContext.builder()
                .traceId("trace-1")
                .runId("run-1")
                .appId("app-1")
                .appName("演示应用")
                .sessionId("session-1")
                .userId("user-1")
                .modelId("model-id-1")
                .build()
                .toListenerAttributes();
    }

    private ILlmTraceRecorder.LlmTraceRecord captureSuccess() {
        ArgumentCaptor<ILlmTraceRecorder.LlmTraceRecord> captor =
                ArgumentCaptor.forClass(ILlmTraceRecorder.LlmTraceRecord.class);
        verify(llmTraceRecorder).recordSuccess(captor.capture());
        return captor.getValue();
    }

    private ILlmTraceRecorder.LlmTraceRecord captureFailure() {
        ArgumentCaptor<ILlmTraceRecorder.LlmTraceRecord> captor =
                ArgumentCaptor.forClass(ILlmTraceRecorder.LlmTraceRecord.class);
        verify(llmTraceRecorder, never()).recordSuccess(any());
        verify(llmTraceRecorder).recordFailure(captor.capture(), anyString());
        return captor.getValue();
    }
}
