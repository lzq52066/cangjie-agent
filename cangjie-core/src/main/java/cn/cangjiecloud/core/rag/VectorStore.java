package cn.cangjiecloud.core.rag;

import java.util.List;

/**
 * 向量存储接口
 * <p>
 * 负责向量的持久化存储与相似度检索。
 * 默认实现基于 pgvector，未来可扩展 Milvus、Qdrant 等。
 */
public interface VectorStore {

    /**
     * 存储向量
     *
     * @param paragraphId  段落 ID
     * @param embedding    向量
     * @param content      原文
     * @param metadata     元数据
     */
    void store(String paragraphId, float[] embedding, String content, java.util.Map<String, Object> metadata);

    /**
     * 批量存储
     */
    void storeBatch(List<VectorEntry> entries);

    /**
     * 向量相似度检索
     *
     * @param queryEmbedding 查询向量
     * @param knowledgeBaseId 知识库 ID（限定检索范围）
     * @param topK           返回数量
     * @return 检索结果列表
     */
    List<RetrievalResult> search(float[] queryEmbedding, String knowledgeBaseId, int topK);

    /**
     * 全文检索
     *
     * @param query           查询文本
     * @param knowledgeBaseId 知识库 ID
     * @param topK            返回数量
     * @return 检索结果列表
     */
    List<RetrievalResult> fullTextSearch(String query, String knowledgeBaseId, int topK);

    /**
     * 向量相似度检索（多知识库单查）。
     * <p>
     * 默认逐库循环再拼接，实现方应覆盖成一条 {@code IN (...)} 查询：
     * 一方面省掉 N 次网络往返，另一方面拼接后名次会错乱（每个库各自从第 1 名开始）。
     */
    default List<RetrievalResult> search(float[] queryEmbedding, java.util.Collection<String> knowledgeBaseIds, int topK) {
        List<RetrievalResult> merged = new java.util.ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            merged.addAll(search(queryEmbedding, kbId, topK));
        }
        return merged;
    }

    /**
     * 全文检索（多知识库 + 限定文档范围）。
     * <p>
     * documentIds 非空时只在这些文档内检索：two_stage 第二阶段靠它把候选文档下推到 SQL，
     * 而不是先全库检索再在内存里过滤（那样 topK 会被无关文档占满，范围内的命中反而进不来）。
     * documentIds 为 null / 空表示不限定文档。
     */
    default List<RetrievalResult> fullTextSearch(String query, java.util.Collection<String> knowledgeBaseIds,
                                                 java.util.Collection<String> documentIds, int topK) {
        List<RetrievalResult> merged = new java.util.ArrayList<>();
        boolean restrict = documentIds != null && !documentIds.isEmpty();
        for (String kbId : knowledgeBaseIds) {
            for (RetrievalResult r : fullTextSearch(query, kbId, topK)) {
                if (!restrict || documentIds.contains(r.getDocumentId())) {
                    merged.add(r);
                }
            }
        }
        return merged;
    }

    /**
     * 删除知识库下所有向量
     */
    void deleteByKnowledgeBase(String knowledgeBaseId);

    /**
     * 删除文档下所有向量
     */
    void deleteByDocument(String documentId);

    // ==================== 多粒度索引：文档级摘要向量 ====================

    /**
     * 存储文档级摘要向量
     *
     * @param documentId 文档 ID
     * @param embedding  摘要向量
     * @param summary    摘要文本
     */
    void storeDocumentSummary(String documentId, float[] embedding, String summary);

    /**
     * 检索文档级摘要（two-stage 第一阶段：筛选候选文档）
     *
     * @param queryEmbedding  查询向量
     * @param knowledgeBaseId 知识库 ID
     * @param topK            候选文档数量上限
     * @return 文档 ID 列表，按相似度降序
     */
    List<String> searchDocumentSummaries(float[] queryEmbedding, String knowledgeBaseId, int topK);

    /**
     * 检索文档级摘要（多知识库）：默认逐库循环，实现方可覆盖为单查。
     */
    default List<String> searchDocumentSummaries(float[] queryEmbedding, java.util.Collection<String> knowledgeBaseIds, int topK) {
        List<String> merged = new java.util.ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            for (String docId : searchDocumentSummaries(queryEmbedding, kbId, topK)) {
                if (!merged.contains(docId)) {
                    merged.add(docId);
                }
            }
        }
        return merged;
    }

    /**
     * 按文档 ID 列表做段落级向量检索（two-stage 第二阶段：精准段落检索）
     */
    List<RetrievalResult> searchByDocumentIds(float[] queryEmbedding, List<String> documentIds, int topK);

    /**
     * 向量条目
     */
    record VectorEntry(String paragraphId, float[] embedding, String content,
                       String knowledgeBaseId, String documentId,
                       java.util.Map<String, Object> metadata) {}
}
