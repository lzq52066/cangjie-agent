package cn.cangjiecloud.core.eval;

import java.util.List;

/**
 * 指标用的向量化端口。
 * <p>
 * answer relevancy 这类「问题—回答语义一致度」指标要算余弦相似度，需要和检索侧
 * 用同一个 embedding 模型，否则空间不对齐、分数没有可比性。没有配 embedding 模型时
 * 传 null，对应指标自动跳过（不硬凑一个基于字面重叠的伪相似度冒充语义分）。
 */
@FunctionalInterface
public interface EmbeddingFunction {

    /**
     * 批量向量化。
     *
     * @param texts 文本列表
     * @return 与入参等长、同序的向量列表
     */
    List<float[]> embed(List<String> texts);
}
