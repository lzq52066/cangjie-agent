package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于 pgvector 的向量存储实现
 * <p>
 * 向量存储在 knowledge_paragraph 表的 embedding 列（vector 类型）。
 * 全文检索使用 pgroonga（content &@~ 查询，需先初始化 pgroonga 扩展）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void store(String paragraphId, float[] embedding, String content, Map<String, Object> metadata) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update(
                "UPDATE knowledge_paragraph SET embedding = ?::vector, vector_status = 'embedded' WHERE id = ?",
                vectorStr, paragraphId
        );
    }

    @Override
    public void storeBatch(List<VectorEntry> entries) {
        for (VectorEntry entry : entries) {
            store(entry.paragraphId(), entry.embedding(), entry.content(), entry.metadata());
        }
    }

    @Override
    public List<RetrievalResult> search(float[] queryEmbedding, String knowledgeBaseId, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, document_id, knowledge_base_id, content, title,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_paragraph
                WHERE deleted = 0
                  AND knowledge_base_id = ?
                  AND vector_status = 'embedded'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                vectorStr, knowledgeBaseId, vectorStr, topK
        );

        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(RetrievalResult.builder()
                    .paragraphId((String) row.get("id"))
                    .documentId((String) row.get("document_id"))
                    .knowledgeBaseId((String) row.get("knowledge_base_id"))
                    .content((String) row.get("content"))
                    .vectorScore(((Number) row.get("similarity")).doubleValue())
                    .fullTextScore(0)
                    .finalScore(0)
                    .metadata(extractMetadata(row))
                    .build());
        }
        return results;
    }

    @Override
    public List<RetrievalResult> fullTextSearch(String query, String knowledgeBaseId, int topK) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, document_id, knowledge_base_id, content, title,
                       pgroonga_score(tableoid, ctid) AS rank
                FROM knowledge_paragraph
                WHERE deleted = 0
                  AND knowledge_base_id = ?
                  AND content &@~ ?
                ORDER BY rank DESC
                LIMIT ?
                """,
                knowledgeBaseId, query, topK
        );

        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(RetrievalResult.builder()
                    .paragraphId((String) row.get("id"))
                    .documentId((String) row.get("document_id"))
                    .knowledgeBaseId((String) row.get("knowledge_base_id"))
                    .content((String) row.get("content"))
                    .vectorScore(0)
                    .fullTextScore(((Number) row.get("rank")).doubleValue())
                    .finalScore(0)
                    .metadata(extractMetadata(row))
                    .build());
        }
        return results;
    }

    @Override
    public void deleteByKnowledgeBase(String knowledgeBaseId) {
        jdbcTemplate.update(
                "UPDATE knowledge_paragraph SET embedding = NULL, vector_status = 'pending' WHERE knowledge_base_id = ?",
                knowledgeBaseId
        );
    }

    @Override
    public void deleteByDocument(String documentId) {
        jdbcTemplate.update(
                "UPDATE knowledge_paragraph SET embedding = NULL, vector_status = 'pending' WHERE document_id = ?",
                documentId
        );
    }

    // ==================== 多粒度索引：文档级摘要向量 ====================

    /**
     * 存储文档级摘要向量
     */
    public void storeDocumentSummary(String documentId, float[] embedding, String summary) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update(
                "UPDATE knowledge_document SET summary = ?, summary_embedding = ?::vector WHERE id = ?",
                summary, vectorStr, documentId
        );
    }

    /**
     * 检索文档级摘要（two-stage 第一阶段：筛选候选文档）
     *
     * @return 文档 ID 列表，按相似度降序
     */
    public List<String> searchDocumentSummaries(float[] queryEmbedding, String knowledgeBaseId, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, 1 - (summary_embedding <=> ?::vector) AS similarity
                FROM knowledge_document
                WHERE deleted = 0
                  AND knowledge_base_id = ?
                  AND summary_embedding IS NOT NULL
                ORDER BY summary_embedding <=> ?::vector
                LIMIT ?
                """,
                vectorStr, knowledgeBaseId, vectorStr, topK
        );
        return rows.stream().map(r -> (String) r.get("id")).toList();
    }

    /**
     * 按文档 ID 列表做段落级向量检索（two-stage 第二阶段：精准段落检索）
     */
    public List<RetrievalResult> searchByDocumentIds(float[] queryEmbedding, List<String> documentIds, int topK) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        String vectorStr = toVectorString(queryEmbedding);
        // 参数化 IN 子句，避免字符串拼接 SQL
        String placeholders = String.join(",", java.util.Collections.nCopies(documentIds.size(), "?"));
        String sql = """
                SELECT id, document_id, knowledge_base_id, content, title,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_paragraph
                WHERE deleted = 0
                  AND vector_status = 'embedded'
                  AND document_id IN (%s)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders);

        List<Object> args = new ArrayList<>(documentIds);
        args.add(vectorStr);
        args.add(vectorStr);
        args.add(topK);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());

        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(RetrievalResult.builder()
                    .paragraphId((String) row.get("id"))
                    .documentId((String) row.get("document_id"))
                    .knowledgeBaseId((String) row.get("knowledge_base_id"))
                    .content((String) row.get("content"))
                    .vectorScore(((Number) row.get("similarity")).doubleValue())
                    .fullTextScore(0)
                    .finalScore(0)
                    .metadata(extractMetadata(row))
                    .build());
        }
        return results;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
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
