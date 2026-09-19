package cn.cangjiecloud.model.provider;

import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.core.model.LlmErrorMapper;
import cn.cangjiecloud.core.model.TokenEstimator;
import cn.cangjiecloud.core.workflow.RetryExecutor;
import cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker;
import cn.cangjiecloud.model.entity.ModelEntity;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * OpenAI 兼容模型客户端（深度集成 langchain4j 1.18.1）
 * <p>
 * 使用 langchain4j 的 OpenAiChatModel / OpenAiStreamingChatModel 原生 API，
 * 统一处理 OpenAI / 通义千问 / 智谱 / Ollama 等 OpenAI 兼容接口的调用。
 */
@Slf4j
public class OpenAICompatibleClient {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);

    /** 重试退避倍数：每次重试后延迟翻倍 */
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

    private final ModelEntity modelConfig;
    /** 调用凭证：来自模型关联的厂商，与模型配置彻底分离 */
    private final String apiKey;
    /** 厂商 API 地址 */
    private final String baseUrl;
    /** 单次 LLM 请求超时（同步与流式模型均生效） */
    private final Duration requestTimeout;
    /** 流式生产者任务使用的受管线程池 */
    private final AsyncTaskExecutor streamExecutor;
    /** 模型熔断器（可为 null，表示不参与熔断统计） */
    private final ModelCircuitBreaker circuitBreaker;
    /** LLM 调用监听器（统一可观测入口，可为空列表） */
    private final List<ChatModelListener> listeners;
    /** 瞬时故障最大重试次数（不含首次执行） */
    private final int maxRetries;
    /** 首次重试延迟（毫秒） */
    private final long retryDelayMs;
    /**
     * 该模型是否已探明不支持结构化输出约束（厂商拒绝 response_format）。
     * 本实例由 ModelService 按模型 ID 缓存复用，故一次探测结果对该模型长期生效，
     * 后续调用不再携带约束，避免每次都白跑一次失败请求。
     */
    private volatile boolean structuredOutputRejected = false;

    public OpenAICompatibleClient(ModelEntity modelConfig, String apiKey, String baseUrl,
                                  Duration requestTimeout,
                                  AsyncTaskExecutor streamExecutor, ModelCircuitBreaker circuitBreaker,
                                  List<ChatModelListener> listeners, int maxRetries, long retryDelayMs) {
        this.modelConfig = modelConfig;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.streamExecutor = streamExecutor;
        this.circuitBreaker = circuitBreaker;
        this.listeners = listeners != null ? listeners : List.of();
        this.maxRetries = Math.max(maxRetries, 0);
        this.retryDelayMs = Math.max(retryDelayMs, 0);
    }

    /**
     * 同步对话（支持 Function Calling）
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            ChatResponse response = chatWithRetry(request);
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess(modelConfig.getId());
            }
            return response;
        } catch (RuntimeException e) {
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure(modelConfig.getId());
            }
            throw e;
        }
    }

    /**
     * 仅对瞬时故障（限流 / 5xx / 超时 / 网络）做指数退避重试；
     * 参数非法、鉴权失败等重试无意义的错误按 {@link LlmErrorMapper} 的分类立即抛出。
     */
    private ChatResponse chatWithRetry(ChatRequest request) {
        if (maxRetries <= 0) {
            return doChat(request);
        }
        try {
            return RetryExecutor.execute(() -> doChat(request), maxRetries, retryDelayMs,
                    RETRY_BACKOFF_MULTIPLIER, LlmErrorMapper::isRetriable, "llm-chat");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // RetryExecutor 声明受检异常，而 doChat 仅抛运行时异常，此处仅作兜底
            throw new IllegalStateException(e);
        }
    }

    private ChatResponse doChat(ChatRequest request) {
        ResponseFormat responseFormat = buildResponseFormat(request);
        if (responseFormat == null) {
            return execChat(request, null);
        }
        try {
            return execChat(request, responseFormat);
        } catch (RuntimeException e) {
            if (!isResponseFormatRejected(e)) {
                throw e;
            }
            // 厂商不支持 response_format：记录后永久降级，并重试一次纯提示词约束的调用
            structuredOutputRejected = true;
            log.warn("模型 {} 不支持结构化输出约束，已降级为提示词约束: {}",
                    modelConfig.getModelName(), e.getMessage());
            return execChat(request, null);
        }
    }

    /**
     * 执行一次对话。
     *
     * @param responseFormat 结构化输出约束；null 表示不携带（模型自由输出）
     */
    private ChatResponse execChat(ChatRequest request, ResponseFormat responseFormat) {
        dev.langchain4j.model.chat.ChatModel chatModel = buildChatModel(request, responseFormat);
        List<dev.langchain4j.data.message.ChatMessage> messages = convertMessages(request.getMessages());

        // 构建 langchain4j ChatRequest（携带 tools）
        dev.langchain4j.model.chat.request.ChatRequest.Builder lcBuilder =
                dev.langchain4j.model.chat.request.ChatRequest.builder().messages(messages);

        if (responseFormat != null) {
            lcBuilder.responseFormat(responseFormat);
        }

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            lcBuilder.toolSpecifications(convertTools(request.getTools()));
            lcBuilder.toolChoice(
                request.getToolChoice() != null && !"none".equals(request.getToolChoice())
                    ? dev.langchain4j.model.chat.request.ToolChoice.AUTO
                    : dev.langchain4j.model.chat.request.ToolChoice.NONE
            );
        }

        dev.langchain4j.model.chat.response.ChatResponse lcResponse =
                chatModel.chat(lcBuilder.build(), buildRequestOptions(request));
        AiMessage ai = lcResponse.aiMessage();

        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .content(ai.text())
                .role("assistant")
                .model(modelConfig.getModelName())
                .promptTokens(lcResponse.tokenUsage() != null ? lcResponse.tokenUsage().inputTokenCount() : 0)
                .completionTokens(lcResponse.tokenUsage() != null ? lcResponse.tokenUsage().outputTokenCount() : 0)
                .totalTokens(lcResponse.tokenUsage() != null ? lcResponse.tokenUsage().totalTokenCount() : 0)
                .finishReason(lcResponse.finishReason() != null ? lcResponse.finishReason().name() : null);

        // 解析工具调用
        if (ai.hasToolExecutionRequests()) {
            List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
            for (dev.langchain4j.agent.tool.ToolExecutionRequest req : ai.toolExecutionRequests()) {
                toolCalls.add(ChatResponse.ToolCall.builder()
                        .id(req.id())
                        .name(req.name())
                        .arguments(req.arguments())
                        .build());
            }
            builder.toolCalls(toolCalls);
        }

        return builder.build();
    }

    /**
     * 流式对话 — 使用 LangChain4j 原生 token 级流式推送
     */
    public Stream<ChatChunk> streamChat(ChatRequest request) {
        return streamChat(request, null);
    }

    /**
     * 流式对话（可取消）
     *
     * @param cancelSignal 外部取消信号：消费方断开或超时时置 true，
     *                     流停止读取并中断生产者任务，避免后台线程空转
     */
    public Stream<ChatChunk> streamChat(ChatRequest request, AtomicBoolean cancelSignal) {
        if (streamExecutor == null) {
            throw new IllegalStateException("模型客户端未配置流式线程池，无法发起流式调用");
        }
        BlockingQueue<ChatChunk> queue = new LinkedBlockingQueue<>();
        List<dev.langchain4j.data.message.ChatMessage> messages = convertMessages(request.getMessages());

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
                .timeout(requestTimeout)
                .listeners(listeners)
                .build();

        dev.langchain4j.model.chat.request.ChatRequest.Builder lcBuilder =
                dev.langchain4j.model.chat.request.ChatRequest.builder().messages(messages);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            lcBuilder.toolSpecifications(convertTools(request.getTools()));
            lcBuilder.toolChoice(
                request.getToolChoice() != null && !"none".equals(request.getToolChoice())
                    ? dev.langchain4j.model.chat.request.ToolChoice.AUTO
                    : dev.langchain4j.model.chat.request.ToolChoice.NONE
            );
        }

        dev.langchain4j.model.chat.request.ChatRequest lcRequest = lcBuilder.build();
        ChatRequestOptions lcOptions = buildRequestOptions(request);

        Future<?> producer = streamExecutor.submit(() -> {
            // 累积正文用于 usage 缺失时兜底估算（仅生产者线程访问）
            StringBuilder streamedText = new StringBuilder();
            try {
                streamingModel.chat(lcRequest, lcOptions, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        try {
                            streamedText.append(token);
                            queue.put(ChatChunk.builder().delta(token).done(false).build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                        if (circuitBreaker != null) {
                            circuitBreaker.recordSuccess(modelConfig.getId());
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        try {
                            String finishReason = response.finishReason() != null ? response.finishReason().name() : "stop";
                            ChatChunk.ChatChunkBuilder chunkBuilder = ChatChunk.builder()
                                    .delta("")
                                    .done(true)
                                    .finishReason(finishReason);

                            // 优先取服务端真实 usage；部分 OpenAI 兼容厂商不支持 stream_options.include_usage，
                            // 此时回落为 jtokkit 估算，避免 trace / 会话统计记成 0
                            var usage = response.tokenUsage();
                            if (usage != null && usage.totalTokenCount() > 0) {
                                chunkBuilder.inputTokens((long) usage.inputTokenCount())
                                        .outputTokens((long) usage.outputTokenCount())
                                        .totalTokens((long) usage.totalTokenCount());
                            } else {
                                long estimatedIn = TokenEstimator.count(request.getMessages());
                                long estimatedOut = TokenEstimator.count(streamedText.toString());
                                chunkBuilder.inputTokens(estimatedIn)
                                        .outputTokens(estimatedOut)
                                        .totalTokens(estimatedIn + estimatedOut);
                                log.debug("流式响应未返回 usage，已按 jtokkit 估算: input={}, output={}",
                                        estimatedIn, estimatedOut);
                            }

                            // 流式响应中的工具调用
                            AiMessage ai = response.aiMessage();
                            if (ai.hasToolExecutionRequests()) {
                                List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
                                for (dev.langchain4j.agent.tool.ToolExecutionRequest req : ai.toolExecutionRequests()) {
                                    toolCalls.add(ChatResponse.ToolCall.builder()
                                            .id(req.id())
                                            .name(req.name())
                                            .arguments(req.arguments())
                                            .build());
                                }
                                chunkBuilder.toolCalls(toolCalls);
                            }

                            queue.put(chunkBuilder.build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式调用模型失败: {}", error.getMessage());
                        if (circuitBreaker != null) {
                            circuitBreaker.recordFailure(modelConfig.getId());
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        String friendlyMsg = LlmErrorMapper.map(error);
                        try {
                            queue.put(ChatChunk.builder()
                                    .delta("")
                                    .done(true)
                                    .error(friendlyMsg)
                                    .build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            } catch (Exception e) {
                log.error("流式调用模型异常: {}", e.getMessage(), e);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    queue.put(ChatChunk.builder().delta("").done(true).error(LlmErrorMapper.map(e)).build());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<ChatChunk>(Long.MAX_VALUE, Spliterator.ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super ChatChunk> action) {
                        try {
                            while (true) {
                                if (isCancelled(cancelSignal)) {
                                    return false;
                                }
                                ChatChunk chunk = queue.poll(500, TimeUnit.MILLISECONDS);
                                if (chunk == null) {
                                    continue;
                                }
                                action.accept(chunk);
                                return !chunk.isDone();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                },
                false
        ).onClose(() -> producer.cancel(true));
    }

    private static boolean isCancelled(AtomicBoolean cancelSignal) {
        return (cancelSignal != null && cancelSignal.get()) || Thread.currentThread().isInterrupted();
    }

    /**
     * 文本嵌入
     */
    public float[] embed(String text) {
        if (!Boolean.TRUE.equals(modelConfig.getSupportEmbedding())) {
            throw new UnsupportedOperationException("该模型不支持嵌入: " + modelConfig.getName());
        }

        dev.langchain4j.model.openai.OpenAiEmbeddingModel embeddingModel =
                dev.langchain4j.model.openai.OpenAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(modelConfig.getModelName())
                        .dimensions(modelConfig.getEmbeddingDimension())
                        .timeout(requestTimeout)
                        .build();

        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }

    // ============ 私有方法 ============

    /**
     * 由请求中的 responseSchema 构建结构化输出约束。
     * 未指定 schema 或该模型已被探明不支持时返回 null（退化为纯提示词约束）。
     */
    private ResponseFormat buildResponseFormat(ChatRequest request) {
        if (structuredOutputRejected || !StringUtils.hasText(request.getResponseSchema())) {
            return null;
        }
        String schemaName = StringUtils.hasText(request.getResponseSchemaName())
                ? request.getResponseSchemaName() : "structured_output";
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name(schemaName)
                        .rootElement(JsonRawSchema.from(request.getResponseSchema()))
                        .build())
                .build();
    }

    /**
     * 判断异常是否为厂商拒绝 response_format 参数（400 且错误信息指向该参数）。
     * 仅此类错误才值得去掉约束重试，其余错误原样抛出。
     */
    private static boolean isResponseFormatRejected(RuntimeException e) {
        boolean badRequest = (e instanceof HttpException http && http.statusCode() == 400)
                || e instanceof InvalidRequestException;
        if (!badRequest || e.getMessage() == null) {
            return false;
        }
        String message = e.getMessage().toLowerCase();
        return message.contains("response_format") || message.contains("response format")
                || message.contains("json_schema") || message.contains("json schema");
    }

    private dev.langchain4j.model.chat.ChatModel buildChatModel(ChatRequest request, ResponseFormat responseFormat) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
                .timeout(requestTimeout)
                .strictTools(true)
                // 仅在本次调用携带 schema 时开启严格模式，避免影响普通对话
                .strictJsonSchema(responseFormat != null && responseFormat.jsonSchema() != null)
                .listeners(listeners)
                .build();
    }

    /**
     * 把请求上的业务上下文转成 langchain4j 的按调用选项。
     * 监听器据此把 trace 归集到正确的链路/应用/会话，且不受线程切换（流式）影响。
     */
    private static ChatRequestOptions buildRequestOptions(ChatRequest request) {
        ChatTraceContext traceContext = request.getTraceContext();
        if (traceContext == null) {
            return ChatRequestOptions.EMPTY;
        }
        java.util.Map<Object, Object> attributes = traceContext.toListenerAttributes();
        if (attributes.isEmpty()) {
            return ChatRequestOptions.EMPTY;
        }
        return ChatRequestOptions.builder().listenerAttributes(attributes).build();
    }

    private List<dev.langchain4j.data.message.ChatMessage> convertMessages(List<ChatMessage> messages) {
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();
        if (messages == null) return result;
        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case "system" -> result.add(SystemMessage.from(msg.getContent()));
                case "user" -> result.add(UserMessage.from(msg.getContent()));
                case "assistant" -> result.add(toAiMessage(msg));
                case "tool" -> result.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                        msg.getToolCallId() != null ? msg.getToolCallId() : "",
                        msg.getToolName() != null ? msg.getToolName() : "",
                        msg.getContent()));
                default -> result.add(UserMessage.from(msg.getContent()));
            }
        }
        return result;
    }

    /**
     * assistant 消息转换：若携带工具调用，必须连同 tool_calls（id/name/arguments）一起回传，
     * 否则紧随其后的 tool 结果消息会被厂商判为非法序列。
     */
    private AiMessage toAiMessage(ChatMessage msg) {
        List<ChatMessage.ToolCallRef> calls = msg.getToolCalls();
        if (calls == null || calls.isEmpty()) {
            return AiMessage.from(msg.getContent() == null ? "" : msg.getContent());
        }
        List<ToolExecutionRequest> requests = calls.stream()
                .map(tc -> ToolExecutionRequest.builder()
                        .id(tc.getId())
                        .name(tc.getName())
                        .arguments(tc.getArguments() == null ? "{}" : tc.getArguments())
                        .build())
                .toList();
        return new AiMessage(msg.getContent() == null ? "" : msg.getContent(), requests);
    }

    @SuppressWarnings("unchecked")
    private List<dev.langchain4j.agent.tool.ToolSpecification> convertTools(
            List<Map<String, Object>> tools) {
        List<dev.langchain4j.agent.tool.ToolSpecification> result = new ArrayList<>();
        if (tools == null) return result;
        for (Map<String, Object> tool : tools) {
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            if (function == null) continue;
            String name = (String) function.get("name");
            String description = (String) function.get("description");
            Map<String, Object> params = (Map<String, Object>) function.get("parameters");

            dev.langchain4j.agent.tool.ToolSpecification.Builder specBuilder =
                    dev.langchain4j.agent.tool.ToolSpecification.builder()
                            .name(name)
                            .description(description != null ? description : "");

            if (params != null) {
                specBuilder.parameters(buildJsonSchema(params));
            }

            result.add(specBuilder.build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private JsonObjectSchema buildJsonSchema(Map<String, Object> params) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                String type = (String) prop.get("type");
                String desc = (String) prop.get("description");
                addProperty(builder, entry.getKey(), type, desc != null ? desc : "");
            }
        }
        List<String> required = (List<String>) params.get("required");
        if (required != null && !required.isEmpty()) {
            builder.required(required);
        }
        return builder.build();
    }

    private void addProperty(JsonObjectSchema.Builder builder, String name, String type, String desc) {
        if ("string".equals(type)) {
            builder.addStringProperty(name, desc);
        } else if ("integer".equals(type)) {
            builder.addIntegerProperty(name, desc);
        } else if ("number".equals(type)) {
            builder.addNumberProperty(name, desc);
        } else if ("boolean".equals(type)) {
            builder.addBooleanProperty(name, desc);
        }
        // 其他复杂类型（array/object）暂时跳过，不影响主流场景
    }
}