package cn.cangjiecloud.core.model;

/**
 * 模型提供者接口
 * <p>
 * 统一不同大模型（OpenAI、通义千问、智谱等）的调用入口。
 * 每个提供者通过 {@link ModelType} 标识，由 ModelProviderFactory 动态选择。
 */
public interface ModelProvider {

    /**
     * 同步对话
     *
     * @param request 对话请求
     * @return 对话响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话
     *
     * @param request 对话请求
     * @return 流式响应（逐 token 推送）
     */
    java.util.stream.Stream<ChatChunk> streamChat(ChatRequest request);

    /**
     * 文本嵌入（可选实现）
     *
     * @param text 文本
     * @return 向量
     */
    default float[] embed(String text) {
        throw new UnsupportedOperationException("该模型不支持嵌入");
    }

    /**
     * 模型类型
     */
    ModelType getType();
}
