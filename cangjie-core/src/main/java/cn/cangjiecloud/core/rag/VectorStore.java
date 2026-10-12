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
     * 向量相似度检索（多知识库）。
     * <p>
     * 默认逐库循环后按相似度全局重排、按段落去重、截到 topK。实现方应覆盖成一条
     * {@code IN (...)} 查询：省掉 N 次网络往返，且每条通道只取一次 topK，
     * 而不是每个库各取 topK 再挑。
     */
    default List<RetrievalResult> search(float[] queryEmbedding, java.util.Collection<String> knowledgeBaseIds, int topK) {
        java.util.Map<String, RetrievalResult> byParagraph = new java.util.LinkedHashMap<>();
        for (String kbId : knowledgeBaseIds) {
            for (RetrievalResult r : search(queryEmbedding, kbId, topK)) {
                byParagraph.merge(paragraphKey(r), r,
                        (old, neu) -> neu.getVectorScore() > old.getVectorScore() ? neu : old);
            }
        }
        return byParagraph.values().stream()
                .sorted(java.util.Comparator.comparingDouble(RetrievalResult::getVectorScore).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 全文检索（多知识库 + 限定文档范围）。
     * <p>
     * documentIds 非空时只在这些文档内检索：two_stage 第二阶段靠它把候选文档下推到 SQL，
     * 而不是先全库检索再在内存里过滤（那样 topK 会被无关文档占满，范围内的命中反而进不来）。
     * documentIds 为 null / 空表示不限定文档。
     * <p>
     * 默认实现逐库循环后按全文分全局重排，避免各库各自从第 1 名开始导致的名次错乱。
     */
    default List<RetrievalResult> fullTextSearch(String query, java.util.Collection<String> knowledgeBaseIds,
                                                 java.util.Collection<String> documentIds, int topK) {
        java.util.Map<String, RetrievalResult> byParagraph = new java.util.LinkedHashMap<>();
        boolean restrict = documentIds != null && !documentIds.isEmpty();
        for (String kbId : knowledgeBaseIds) {
            for (RetrievalResult r : fullTextSearch(query, kbId, topK)) {
                if (restrict && !documentIds.contains(r.getDocumentId())) {
                    continue;
                }
                byParagraph.merge(paragraphKey(r), r,
                        (old, neu) -> neu.getFullTextScore() > old.getFullTextScore() ? neu : old);
            }
        }
        return byParagraph.values().stream()
                .sorted(java.util.Comparator.comparingDouble(RetrievalResult::getFullTextScore).reversed())
                .limit(topK)
                .toList();
    }

    /** 段落归并键：没有段落 ID 时退到「文档 + 正文指纹」，保证同一内容在多次调用里映射到同一个键 */
    private static String paragraphKey(RetrievalResult r) {
        if (r.getParagraphId() != null) {
            return r.getParagraphId();
        }
        String content = r.getContent();
        return r.getDocumentId() + "#" + (content == null ? 0 : Integer.toHexString(content.hashCode()));
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
        java.util.Set<String> merged = new java.util.LinkedHashSet<>();
        for (String kbId : knowledgeBaseIds) {
            merged.addAll(searchDocumentSummaries(queryEmbedding, kbId, topK));
        }
        return List.copyOf(merged);
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
