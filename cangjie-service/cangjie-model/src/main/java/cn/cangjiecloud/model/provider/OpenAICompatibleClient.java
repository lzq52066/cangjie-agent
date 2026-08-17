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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * OpenAI 兼容模型客户端（深度集成 langchain4j 1.18.1）
 * <p>
 * 使用 langchain4j 的 OpenAiChatModel 原生 API，
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
        ChatModel chatModel = buildChatModel(request);
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
     * 流式对话（简化实现：同步调用后按句分割模拟流式推送）
     */
    public Stream<ChatChunk> streamChat(ChatRequest request) {
        ChatResponse response = chat(request);
        String content = response.getContent();
        if (content == null || content.isEmpty()) {
            return Stream.of(ChatChunk.builder().delta("").done(true).finishReason(response.getFinishReason()).build());
        }

        List<String> parts = splitForStreaming(content);
        return Stream.concat(
                parts.stream().map(part -> ChatChunk.builder().delta(part).done(false).build()),
                Stream.of(ChatChunk.builder().delta("").done(true).finishReason(response.getFinishReason()).build())
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

    private ChatModel buildChatModel(ChatRequest request) {
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

    private List<String> splitForStreaming(String content) {
        List<String> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (char c : content.toCharArray()) {
            buffer.append(c);
            if (c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?'
                    || c == '；' || c == ';' || c == '\n') {
                parts.add(buffer.toString());
                buffer = new StringBuilder();
            }
        }
        if (buffer.length() > 0) {
            parts.add(buffer.toString());
        }
        return parts;
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
            case CUSTOM -> "https://api.openai.com/v1";
        };
    }
}
