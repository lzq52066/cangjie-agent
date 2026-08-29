package cn.cangjiecloud.model.provider;

import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ModelType;
import cn.cangjiecloud.model.circuitbreaker.ModelCircuitBreaker;
import cn.cangjiecloud.model.entity.ModelEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;

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

    private final ModelEntity modelConfig;
    /** 单次 LLM 请求超时（同步与流式模型均生效） */
    private final Duration requestTimeout;
    /** 流式生产者任务使用的受管线程池 */
    private final AsyncTaskExecutor streamExecutor;
    /** 模型熔断器（可为 null，表示不参与熔断统计） */
    private final ModelCircuitBreaker circuitBreaker;

    public OpenAICompatibleClient(ModelEntity modelConfig, Duration requestTimeout,
                                  AsyncTaskExecutor streamExecutor, ModelCircuitBreaker circuitBreaker) {
        this.modelConfig = modelConfig;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.streamExecutor = streamExecutor;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 同步对话（支持 Function Calling）
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            ChatResponse response = doChat(request);
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

    private ChatResponse doChat(ChatRequest request) {
        dev.langchain4j.model.chat.ChatModel chatModel = buildChatModel(request);
        List<dev.langchain4j.data.message.ChatMessage> messages = convertMessages(request.getMessages());

        // 构建 langchain4j ChatRequest（携带 tools）
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

        dev.langchain4j.model.chat.response.ChatResponse lcResponse = chatModel.chat(lcBuilder.build());
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
                .apiKey(modelConfig.getApiKey())
                .baseUrl(modelConfig.getBaseUrl())
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
                .timeout(requestTimeout)
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

        Future<?> producer = streamExecutor.submit(() -> {
            try {
                streamingModel.chat(lcRequest, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        try {
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
                            var usage = response.tokenUsage();
                            ChatChunk.ChatChunkBuilder chunkBuilder = ChatChunk.builder()
                                    .delta("")
                                    .done(true)
                                    .finishReason(finishReason)
                                    .inputTokens(usage != null ? (long) usage.inputTokenCount() : null)
                                    .outputTokens(usage != null ? (long) usage.outputTokenCount() : null)
                                    .totalTokens(usage != null ? (long) usage.totalTokenCount() : null);

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
                        String friendlyMsg = LlmErrorMapper.map(error.getMessage());
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
                    queue.put(ChatChunk.builder().delta("").done(true).error(LlmErrorMapper.map(e.getMessage())).build());
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
                        .apiKey(modelConfig.getApiKey())
                        .baseUrl(modelConfig.getBaseUrl())
                        .modelName(modelConfig.getModelName())
                        .dimensions(modelConfig.getEmbeddingDimension())
                        .timeout(requestTimeout)
                        .build();

        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }

    // ============ 私有方法 ============

    private dev.langchain4j.model.chat.ChatModel buildChatModel(ChatRequest request) {
        return OpenAiChatModel.builder()
                .apiKey(modelConfig.getApiKey())
                .baseUrl(modelConfig.getBaseUrl())
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
                .timeout(requestTimeout)
                .strictTools(true)
                .build();
    }

    private List<dev.langchain4j.data.message.ChatMessage> convertMessages(List<ChatMessage> messages) {
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();
        if (messages == null) return result;
        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case "system" -> result.add(SystemMessage.from(msg.getContent()));
                case "user" -> result.add(UserMessage.from(msg.getContent()));
                case "assistant" -> result.add(AiMessage.from(msg.getContent()));
                case "tool" -> result.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                        msg.getToolName() != null ? msg.getToolName() : "",
                        msg.getToolCallId() != null ? msg.getToolCallId() : "",
                        msg.getContent()));
                default -> result.add(UserMessage.from(msg.getContent()));
            }
        }
        return result;
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

    /**
     * 各模型提供商默认 baseUrl
     */
    public static String defaultBaseUrl(ModelType type) {
        return switch (type) {
            case OPENAI -> "https://api.openai.com/v1";
            case DEEPSEEK -> "https://api.deepseek.com/v1";
            case QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case ZHIPU -> "https://open.bigmodel.cn/api/paas/v4";
            case WENXIN -> "https://qianfan.baidubce.com/v2";
            case OLLAMA -> "http://localhost:11434/v1";
            case CUSTOM -> "";
        };
    }
}