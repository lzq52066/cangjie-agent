package cn.cangjiecloud.model.provider;

import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ModelType;
import cn.cangjiecloud.model.entity.ModelEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

    private final ModelEntity modelConfig;

    public OpenAICompatibleClient(ModelEntity modelConfig) {
        this.modelConfig = modelConfig;
    }

    /**
     * 同步对话
     */
    public ChatResponse chat(ChatRequest request) {
        dev.langchain4j.model.chat.ChatModel chatModel = buildChatModel(request);
        List<dev.langchain4j.data.message.ChatMessage> messages = convertMessages(request.getMessages());
        dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(messages);
        AiMessage ai = response.aiMessage();

        return ChatResponse.builder()
                .content(ai.text())
                .role("assistant")
                .model(modelConfig.getModelName())
                .promptTokens(response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0)
                .completionTokens(response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0)
                .totalTokens(response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0)
                .finishReason(response.finishReason() != null ? response.finishReason().name() : null)
                .build();
    }

    /**
     * 流式对话 — 使用 LangChain4j 原生 token 级流式推送
     */
    public Stream<ChatChunk> streamChat(ChatRequest request) {
        BlockingQueue<ChatChunk> queue = new LinkedBlockingQueue<>();
        List<dev.langchain4j.data.message.ChatMessage> messages = convertMessages(request.getMessages());

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(modelConfig.getApiKey())
                .baseUrl(modelConfig.getBaseUrl())
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
                .build();

        dev.langchain4j.model.chat.request.ChatRequest langchainRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(messages)
                        .build();

        new Thread(() -> {
            try {
                streamingModel.chat(langchainRequest, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        try {
                            queue.put(ChatChunk.builder().delta(token).done(false).build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                        try {
                            String finishReason = response.finishReason() != null ? response.finishReason().name() : "stop";
                            queue.put(ChatChunk.builder().delta("").done(true).finishReason(finishReason).build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式调用模型失败: {}", error.getMessage());
                        try {
                            queue.put(ChatChunk.builder()
                                    .delta("")
                                    .done(true)
                                    .error(error.getMessage())
                                    .build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            } catch (Exception e) {
                log.error("流式调用模型异常: {}", e.getMessage(), e);
                try {
                    queue.put(ChatChunk.builder().delta("").done(true).error(e.getMessage()).build());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "llm-streaming").start();

        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super ChatChunk> action) {
                        try {
                            ChatChunk chunk = queue.take();
                            action.accept(chunk);
                            return !chunk.isDone();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                },
                false
        );
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
                        .build();

        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }

    private dev.langchain4j.model.chat.ChatModel buildChatModel(ChatRequest request) {
        return OpenAiChatModel.builder()
                .apiKey(modelConfig.getApiKey())
                .baseUrl(modelConfig.getBaseUrl())
                .modelName(request.getModel() != null ? request.getModel() : modelConfig.getModelName())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens() > 0 ? request.getMaxTokens() : modelConfig.getMaxTokens())
                .topP(request.getTopP())
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
                default -> result.add(UserMessage.from(msg.getContent()));
            }
        }
        return result;
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