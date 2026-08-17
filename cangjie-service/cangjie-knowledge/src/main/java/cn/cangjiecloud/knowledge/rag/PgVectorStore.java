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
 * 全文检索使用 ts_vector 列（PostgreSQL 原生全文检索）。
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
                "UPDATE knowledge_paragraph SET embedding = ?::vector, ts_vector = to_tsvector('simple', ?), vector_status = 'embedded' WHERE id = ?",
                vectorStr, content, paragraphId
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
                       ts_rank(ts_vector, plainto_tsquery('simple', ?)) AS rank
                FROM knowledge_paragraph
                WHERE deleted = 0
                  AND knowledge_base_id = ?
                  AND ts_vector @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """,
                query, knowledgeBaseId, query, topK
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
                "UPDATE knowledge_paragraph SET embedding = NULL, ts_vector = NULL, vector_status = 'pending' WHERE knowledge_base_id = ?",
                knowledgeBaseId
        );
    }

    @Override
    public void deleteByDocument(String documentId) {
        jdbcTemplate.update(
                "UPDATE knowledge_paragraph SET embedding = NULL, ts_vector = NULL, vector_status = 'pending' WHERE document_id = ?",
                documentId
        );
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
