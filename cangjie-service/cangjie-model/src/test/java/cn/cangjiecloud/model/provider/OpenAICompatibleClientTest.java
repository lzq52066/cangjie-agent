package cn.cangjiecloud.model.provider;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker;
import cn.cangjiecloud.model.entity.ModelEntity;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAICompatibleClient} 非流式行为单元测试。
 * <p>
 * 与既有 {@code OpenAICompatibleClientStreamingUsageTest}（专注流式 usage 回填）互补，
 * 本类通过 Mockito 静态 mock {@link OpenAiChatModel#builder()} 隔离 HTTP 层，
 * 不发起任何真实网络请求，覆盖同步对话的结果转换、消息/工具转换、
 * 结构化输出降级、重试退避、熔断联动与构造兜底规则。
 */
class OpenAICompatibleClientTest {

    private ModelCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = mock(ModelCircuitBreaker.class);
    }

    /** 构造测试用模型配置 */
    private static ModelEntity modelConfig() {
        ModelEntity entity = new ModelEntity();
        entity.setId("model-1");
        entity.setName("测试模型");
        entity.setModelName("gpt-4o");
        entity.setMaxTokens(1024);
        return entity;
    }

    /** 构造被测客户端（默认关闭自动重试） */
    private OpenAICompatibleClient newClient(int maxRetries, long delayMs) {
        return new OpenAICompatibleClient(modelConfig(), "test-key", "https://example.com/v1", null,
                mock(AsyncTaskExecutor.class), circuitBreaker, List.of(), maxRetries, delayMs);
    }

    /** 构造 langchain4j 同步响应 */
    private static dev.langchain4j.model.chat.response.ChatResponse lcResponse(String text, TokenUsage usage) {
        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(usage)
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }

    private static ChatRequest simpleRequest() {
        return ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(ChatMessage.user("你好")))
                .maxTokens(64)
                .build();
    }

    /** 静态 mock OpenAiChatModel 构建过程，返回底层 chat 模型 mock 供打桩 */
    private ChatModel mockChatModelBuild(MockedStatic<OpenAiChatModel> mocked) {
        OpenAiChatModel.OpenAiChatModelBuilder builder =
                mock(OpenAiChatModel.OpenAiChatModelBuilder.class, org.mockito.Answers.RETURNS_SELF);
        // build() 声明返回具体类 OpenAiChatModel，故必须 mock 具体类，否则运行时强转失败
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        mocked.when(OpenAiChatModel::builder).thenReturn(builder);
        when(builder.build()).thenReturn(chatModel);
        return chatModel;
    }

    @Nested
    class SyncChatConversion {

        @Test
        void shouldConvertProviderResponseIntoChatResponse() {
            OpenAICompatibleClient client = newClient(0, 0);
            dev.langchain4j.model.chat.response.ChatResponse provider =
                    lcResponse("你好，我是助手", new TokenUsage(10, 20, 30));

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(provider);

                ChatResponse result = client.chat(simpleRequest());

                assertThat(result.getContent()).isEqualTo("你好，我是助手");
                assertThat(result.getRole()).isEqualTo("assistant");
                assertThat(result.getModel()).isEqualTo("gpt-4o");
                assertThat(result.getPromptTokens()).isEqualTo(10);
                assertThat(result.getCompletionTokens()).isEqualTo(20);
                assertThat(result.getTotalTokens()).isEqualTo(30);
                assertThat(result.getFinishReason()).isEqualTo("STOP");
            }
            verify(circuitBreaker).recordSuccess("model-1");
        }

        @Test
        void shouldTolerateMissingTokenUsageAndFinishReason() {
            // usage / finishReason 缺失时按 0 / null 兜底，不应抛空指针
            dev.langchain4j.model.chat.response.ChatResponse provider =
                    dev.langchain4j.model.chat.response.ChatResponse.builder()
                            .aiMessage(AiMessage.from("ok"))
                            .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(provider);

                ChatResponse result = newClient(0, 0).chat(simpleRequest());

                assertThat(result.getPromptTokens()).isZero();
                assertThat(result.getCompletionTokens()).isZero();
                assertThat(result.getTotalTokens()).isZero();
                assertThat(result.getFinishReason()).isNull();
            }
        }

        @Test
        void shouldPropagateFailureAndRecordBreakerFailure() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                HttpException boom = new HttpException(500, "boom");
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(boom);

                assertThatThrownBy(() -> newClient(0, 0).chat(simpleRequest()))
                        .isSameAs(boom);
            }
            verify(circuitBreaker).recordFailure("model-1");
            verify(circuitBreaker, never()).recordSuccess("model-1");
        }
    }

    @Nested
    class ToolCalls {

        @Test
        void shouldConvertToolExecutionRequestsFromResponse() {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("call-1").name("get_weather").arguments("{\"city\":\"hz\"}").build();
            dev.langchain4j.model.chat.response.ChatResponse provider =
                    dev.langchain4j.model.chat.response.ChatResponse.builder()
                            .aiMessage(new AiMessage("", List.of(request)))
                            .metadata(ChatResponseMetadata.builder()
                                    .finishReason(FinishReason.TOOL_EXECUTION).build())
                            .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(provider);

                ChatResponse result = newClient(0, 0).chat(simpleRequest());

                assertThat(result.getToolCalls()).hasSize(1);
                ChatResponse.ToolCall call = result.getToolCalls().get(0);
                assertThat(call.getId()).isEqualTo("call-1");
                assertThat(call.getName()).isEqualTo("get_weather");
                assertThat(call.getArguments()).isEqualTo("{\"city\":\"hz\"}");
                assertThat(result.getFinishReason()).isEqualTo("TOOL_EXECUTION");
            }
        }

        @Test
        void shouldConvertToolsAndPassAutoToolChoice() {
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(ChatMessage.user("天气如何")))
                    .maxTokens(64)
                    .tools(List.of(Map.of("function", Map.of(
                            "name", "get_weather",
                            "description", "查询天气",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of("city", Map.of(
                                            "type", "string", "description", "城市")),
                                    "required", List.of("city"))))))
                    .toolChoice("auto")
                    .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("", new TokenUsage(1, 1, 2)));

                newClient(0, 0).chat(request);

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel).chat(captor.capture(), any());
                dev.langchain4j.model.chat.request.ChatRequest sent = captor.getValue();
                assertThat(sent.toolSpecifications()).hasSize(1);
                assertThat(sent.toolSpecifications().get(0).name()).isEqualTo("get_weather");
                assertThat(sent.toolChoice()).isEqualTo(ToolChoice.AUTO);
            }
        }

        @Test
        void shouldUseNoneToolChoiceWhenToolChoiceMissingOrNone() {
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(ChatMessage.user("hi")))
                    .maxTokens(16)
                    .tools(List.of(Map.of("function", Map.of("name", "fn"))))
                    .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("x", null));

                newClient(0, 0).chat(request);

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel).chat(captor.capture(), any());
                assertThat(captor.getValue().toolChoice()).isEqualTo(ToolChoice.NONE);
            }
        }

        @Test
        void shouldSkipToolSpecificationsWhenNoToolsGiven() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("x", null));

                newClient(0, 0).chat(simpleRequest());

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel).chat(captor.capture(), any());
                assertThat(captor.getValue().toolSpecifications()).isEmpty();
            }
        }
    }

    @Nested
    class MessageConversion {

        @Test
        void shouldConvertAllMessageRoles() {
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(
                            ChatMessage.system("系统提示"),
                            ChatMessage.user("用户提问"),
                            ChatMessage.assistant("历史回复"),
                            ChatMessage.assistant("", List.of(
                                    ChatMessage.ToolCallRef.of("c1", "fn", "{}"))),
                            ChatMessage.tool("fn", "c1", "工具结果")))
                    .maxTokens(16)
                    .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("done", null));

                newClient(0, 0).chat(request);

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel).chat(captor.capture(), any());
                List<dev.langchain4j.data.message.ChatMessage> messages = captor.getValue().messages();
                assertThat(messages).hasSize(5);
                assertThat(messages.get(0).type().name()).isEqualTo("SYSTEM");
                assertThat(messages.get(1).type().name()).isEqualTo("USER");
                assertThat(messages.get(2).type().name()).isEqualTo("AI");
                assertThat(messages.get(3).type().name()).isEqualTo("AI");
                assertThat(((AiMessage) messages.get(3)).toolExecutionRequests()).hasSize(1);
                assertThat(messages.get(4).type().name()).isEqualTo("TOOL_EXECUTION_RESULT");
            }
        }

        @Test
        void shouldTolerateNullAndUnknownRolesWithoutFailing() {
            // 未知角色按 user 处理；消息列表为 null 时返回空列表
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(ChatMessage.builder().role("weird").content("c").build()))
                    .maxTokens(8)
                    .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("ok", null));

                newClient(0, 0).chat(request);

                verify(chatModel, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any());
            }
        }
    }

    @Nested
    class StructuredOutputFallback {

        private static ChatRequest schemaRequest() {
            return ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(ChatMessage.user("抽取信息")))
                    .maxTokens(32)
                    .responseSchema("{\"type\":\"object\"}")
                    .build();
        }

        @Test
        void shouldBuildResponseFormatWhenSchemaProvided() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("{}", null));

                newClient(0, 0).chat(schemaRequest());

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel).chat(captor.capture(), any());
                assertThat(captor.getValue().responseFormat()).isNotNull();
            }
        }

        @Test
        void shouldRetryWithoutResponseFormatWhenProviderRejectsIt() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                // 第一次携带约束被 400 拒绝，去掉约束后成功
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(new InvalidRequestException("response_format is not supported"))
                        .thenReturn(lcResponse("{\"a\":1}", null));

                ChatResponse result = newClient(0, 0).chat(schemaRequest());

                assertThat(result.getContent()).isEqualTo("{\"a\":1}");
                verify(chatModel, times(2)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any());
            }
        }

        @Test
        void shouldPermanentlySkipResponseFormatAfterOneRejection() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(new HttpException(400, "json_schema unsupported"))
                        .thenReturn(lcResponse("ok1", null))
                        .thenReturn(lcResponse("ok2", null));

                OpenAICompatibleClient client = newClient(0, 0);
                client.chat(schemaRequest());
                // 第二次调用：已记住该模型不支持约束，不再下发 response_format
                client.chat(schemaRequest());

                ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor =
                        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
                verify(chatModel, times(3)).chat(captor.capture(), any());
                assertThat(captor.getAllValues().get(2).responseFormat()).isNull();
            }
        }

        @Test
        void shouldRethrowBadRequestUnrelatedToResponseFormat() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(new InvalidRequestException("something else is wrong"));

                assertThatThrownBy(() -> newClient(0, 0).chat(schemaRequest()))
                        .isInstanceOf(InvalidRequestException.class)
                        .hasMessage("something else is wrong");
                // 与 response_format 无关的 400 不应触发去约束重试
                verify(chatModel, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any());
            }
        }
    }

    @Nested
    class Retry {

        @Test
        void shouldRetryRetriableFailureWithExponentialBackoffThenSucceed() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(new RateLimitException("rate limit exceeded"))
                        .thenReturn(lcResponse("recovered", null));

                ChatResponse result = newClient(2, 0).chat(simpleRequest());

                assertThat(result.getContent()).isEqualTo("recovered");
                verify(chatModel, times(2)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any());
            }
        }

        @Test
        void shouldNotRetryNonRetriableFailure() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenThrow(new InvalidRequestException("invalid api key"));

                assertThatThrownBy(() -> newClient(2, 0).chat(simpleRequest()))
                        .isInstanceOf(InvalidRequestException.class);

                verify(chatModel, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any());
            }
        }
    }

    @Nested
    class TraceOptions {

        @Test
        void shouldMapTraceContextIntoListenerAttributes() {
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4o")
                    .messages(List.of(ChatMessage.user("hi")))
                    .maxTokens(8)
                    .traceContext(ChatTraceContext.builder().requestId("req-1").modelId("model-1").build())
                    .build();

            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("ok", null));

                newClient(0, 0).chat(request);

                verify(chatModel).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),
                        org.mockito.ArgumentMatchers.argThat(options -> options != null
                                && "req-1".equals(options.listenerAttributes()
                                        .get(ChatTraceContext.KEY_REQUEST_ID))));
            }
        }

        @Test
        void shouldUseEmptyOptionsWhenNoTraceContext() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("ok", null));

                newClient(0, 0).chat(simpleRequest());

                // 无 trace 时第二参为 EMPTY 单例（非 null），且其 listenerAttributes 为空
                verify(chatModel).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),
                        org.mockito.ArgumentMatchers.argThat(options -> options != null
                                && options.listenerAttributes().isEmpty()));
            }
        }
    }

    @Nested
    class ConstructionAndOtherBranches {

        @Test
        void shouldFallBackToDefaultsForNullTimeoutAndNullListeners() {
            OpenAICompatibleClient client = new OpenAICompatibleClient(modelConfig(), "k", "u", null,
                    mock(AsyncTaskExecutor.class), null, null, -5, -9);

            assertThat(ReflectionTestUtils.getField(client, "requestTimeout")).isNotNull();
            assertThat((List<?>) ReflectionTestUtils.getField(client, "listeners")).isEmpty();
            assertThat(ReflectionTestUtils.getField(client, "maxRetries")).isEqualTo(0);
            assertThat(ReflectionTestUtils.getField(client, "retryDelayMs")).isEqualTo(0L);
        }

        @Test
        void shouldNotInteractWithBreakerWhenItIsNull() {
            try (MockedStatic<OpenAiChatModel> mocked = mockStatic(OpenAiChatModel.class)) {
                ChatModel chatModel = mockChatModelBuild(mocked);
                when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any()))
                        .thenReturn(lcResponse("ok", null));

                OpenAICompatibleClient client = new OpenAICompatibleClient(modelConfig(), "k", "u", null,
                        mock(AsyncTaskExecutor.class), null, List.of(), 0, 0);
                assertThat(client.chat(simpleRequest()).getContent()).isEqualTo("ok");
            }
        }

        @Test
        void streamChatShouldThrowWhenNoExecutorConfigured() {
            OpenAICompatibleClient client = new OpenAICompatibleClient(modelConfig(), "k", "u", null,
                    null, null, List.of(), 0, 0);

            assertThatThrownBy(() -> client.streamChat(simpleRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("流式线程池");
        }

        @Test
        void embedShouldThrowWhenModelDoesNotSupportEmbedding() {
            ModelEntity config = modelConfig();
            config.setSupportEmbedding(false);
            OpenAICompatibleClient client = new OpenAICompatibleClient(config, "k", "u", null,
                    mock(AsyncTaskExecutor.class), null, List.of(), 0, 0);

            assertThatThrownBy(() -> client.embed("text"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("不支持嵌入");
        }
    }
}
