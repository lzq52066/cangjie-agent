package cn.cangjiecloud.core.rag;

import java.util.List;

/**
 * 混合检索器接口
 * <p>
 * 融合向量检索与全文检索结果，使用 RRF（Reciprocal Rank Fusion）算法排序。
 */
public interface HybridRetriever {

    /**
     * 混合检索
     *
     * @param query           查询文本
     * @param knowledgeBaseId 知识库 ID
     * @param topK            返回数量
     * @return RRF 融合后的检索结果
     */
    List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK);

    /**
     * 混合检索（多知识库）
     *
     * @param query           查询文本
     * @param knowledgeBaseIds 知识库 ID 列表
     * @param topK            返回数量
     * @return RRF 融合后的检索结果
     */
    List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK);
}
