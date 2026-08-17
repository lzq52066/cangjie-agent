package cn.cangjiecloud.core.rag;

/**
 * 向量嵌入提供者接口
 * <p>
 * 将文本转换为向量表示，支持不同 Embedding 模型（OpenAI、本地模型等）。
 */
public interface EmbeddingProvider {

    /**
     * 将单条文本嵌入为向量
     *
     * @param text 文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量嵌入
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    java.util.List<float[]> embedBatch(java.util.List<String> texts);

    /**
     * 向量维度
     */
    int dimension();
}
