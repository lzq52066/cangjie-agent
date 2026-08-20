package cn.cangjiecloud.core.rag;

import java.util.List;

/**
 * 混合检索器接口
 * <p>
 * 融合向量检索与全文检索结果，使用加权融合算法排序。
 */
public interface HybridRetriever {

    /**
     * 混合检索（兼容旧接口，不过滤）
     */
    default List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK) {
        return retrieve(query, knowledgeBaseId, topK, 0.0);
    }

    /**
     * 混合检索（兼容旧接口，不过滤）
     */
    default List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK) {
        return retrieve(query, knowledgeBaseIds, topK, 0.0);
    }

    /**
     * 混合检索
     *
     * @param query              查询文本
     * @param knowledgeBaseId    知识库 ID
     * @param topK               返回数量上限
     * @param similarityThreshold 相似度阈值，低于此值的结果将被过滤（0.0 表示不过滤）
     * @return 加权融合后的检索结果
     */
    List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK, double similarityThreshold);

    /**
     * 混合检索（多知识库）
     *
     * @param query               查询文本
     * @param knowledgeBaseIds    知识库 ID 列表
     * @param topK                返回数量上限
     * @param similarityThreshold 相似度阈值，低于此值的结果将被过滤（0.0 表示不过滤）
     * @return 加权融合后的检索结果
     */
    List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK, double similarityThreshold);
}