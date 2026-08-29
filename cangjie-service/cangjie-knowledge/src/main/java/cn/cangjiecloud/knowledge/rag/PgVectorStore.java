package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.mapper.KnowledgeVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 pgvector 的向量存储实现
 * <p>
 * 向量存储在 knowledge_paragraph 表的 embedding 列（vector 类型）。
 * 全文检索使用 pgroonga（content &@~ 查询，需先初始化 pgroonga 扩展）。
 * <p>
 * 全部 SQL 由 {@link KnowledgeVectorMapper} 注解承载（MyBatis-Plus 体系），
 * 本类只负责向量序列化与结果映射。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorStore implements VectorStore {

    /** 单条 UPDATE ... FROM (VALUES ...) 批量回写的分片大小 */
    private static final int BATCH_CHUNK_SIZE = 500;

    private final KnowledgeVectorMapper vectorMapper;

    @Override
    public void store(String paragraphId, float[] embedding, String content, Map<String, Object> metadata) {
        vectorMapper.updateEmbedding(paragraphId, toVectorString(embedding));
    }

    @Override
    public void storeBatch(List<VectorEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<VectorUpdateItem> items = new ArrayList<>(entries.size());
        for (VectorEntry entry : entries) {
            items.add(new VectorUpdateItem(entry.paragraphId(), toVectorString(entry.embedding())));
        }
        for (int from = 0; from < items.size(); from += BATCH_CHUNK_SIZE) {
            vectorMapper.updateEmbeddingBatch(
                    items.subList(from, Math.min(from + BATCH_CHUNK_SIZE, items.size())));
        }
    }

    @Override
    public List<RetrievalResult> search(float[] queryEmbedding, String knowledgeBaseId, int topK) {
        List<Map<String, Object>> rows =
                vectorMapper.searchByVector(toVectorString(queryEmbedding), knowledgeBaseId, topK);
        return mapRows(rows, true);
    }

    @Override
    public List<RetrievalResult> fullTextSearch(String query, String knowledgeBaseId, int topK) {
        List<Map<String, Object>> rows = vectorMapper.searchByFullText(query, knowledgeBaseId, topK);
        return mapRows(rows, false);
    }

    @Override
    public void deleteByKnowledgeBase(String knowledgeBaseId) {
        vectorMapper.clearEmbeddingByKb(knowledgeBaseId);
    }

    @Override
    public void deleteByDocument(String documentId) {
        vectorMapper.clearEmbeddingByDocument(documentId);
    }

    // ==================== 多粒度索引：文档级摘要向量 ====================

    /**
     * 存储文档级摘要向量
     */
    public void storeDocumentSummary(String documentId, float[] embedding, String summary) {
        vectorMapper.updateDocumentSummary(documentId, summary, toVectorString(embedding));
    }

    /**
     * 检索文档级摘要（two-stage 第一阶段：筛选候选文档）
     *
     * @return 文档 ID 列表，按相似度降序
     */
    public List<String> searchDocumentSummaries(float[] queryEmbedding, String knowledgeBaseId, int topK) {
        return vectorMapper.searchSummaryDocIds(toVectorString(queryEmbedding), knowledgeBaseId, topK);
    }

    /**
     * 按文档 ID 列表做段落级向量检索（two-stage 第二阶段：精准段落检索）
     */
    public List<RetrievalResult> searchByDocumentIds(float[] queryEmbedding, List<String> documentIds, int topK) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows =
                vectorMapper.searchByDocumentIds(toVectorString(queryEmbedding), documentIds, topK);
        return mapRows(rows, true);
    }

    /**
     * 按段落 ID 列表取回段落内容（问题路召回：先命中问题再取段落）
     */
    public List<RetrievalResult> getByParagraphIds(List<String> paragraphIds, int limit) {
        if (paragraphIds == null || paragraphIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = vectorMapper.selectByParagraphIds(paragraphIds, limit);
        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(buildResult(row, 0, 0));
        }
        return results;
    }

    // ==================== 私有方法 ====================

    /**
     * 行 → RetrievalResult 映射
     *
     * @param vectorRow true 取 similarity 填向量分；false 取 rank 填全文分
     */
    private List<RetrievalResult> mapRows(List<Map<String, Object>> rows, boolean vectorRow) {
        List<RetrievalResult> results = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            double vectorScore = vectorRow && row.get("similarity") != null
                    ? ((Number) row.get("similarity")).doubleValue() : 0;
            double fullTextScore = !vectorRow && row.get("rank") != null
                    ? ((Number) row.get("rank")).doubleValue() : 0;
            results.add(buildResult(row, vectorScore, fullTextScore));
        }
        return results;
    }

    private RetrievalResult buildResult(Map<String, Object> row, double vectorScore, double fullTextScore) {
        return RetrievalResult.builder()
                .paragraphId((String) row.get("id"))
                .documentId((String) row.get("document_id"))
                .knowledgeBaseId((String) row.get("knowledge_base_id"))
                .content((String) row.get("content"))
                .vectorScore(vectorScore)
                .fullTextScore(fullTextScore)
                .finalScore(0)
                .metadata(extractMetadata(row))
                .build();
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, Object> extractMetadata(Map<String, Object> row) {
        Map<String, Object> meta = new HashMap<>();
        Object title = row.get("title");
        if (title != null) {
            meta.put("title", title);
        }
        return meta;
    }
}
