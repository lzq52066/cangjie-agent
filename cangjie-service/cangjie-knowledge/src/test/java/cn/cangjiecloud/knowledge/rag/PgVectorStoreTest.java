package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.mapper.KnowledgeVectorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PgVectorStore} 单元测试。
 * <p>
 * 覆盖：向量序列化、批量分片回写、行→结果映射、空集合短路与各删除/摘要方法委托。
 */
@ExtendWith(MockitoExtension.class)
class PgVectorStoreTest {

    @Mock
    private KnowledgeVectorMapper vectorMapper;

    private PgVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new PgVectorStore(vectorMapper);
    }

    private static Map<String, Object> row(String id, String docId, String kbId, String content, String title) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("document_id", docId);
        row.put("knowledge_base_id", kbId);
        row.put("content", content);
        if (title != null) {
            row.put("title", title);
        }
        return row;
    }

    @Nested
    class 写入 {

        @Test
        void storeShouldSerializeVectorAndDelegateToMapper() {
            vectorStore.store("p1", new float[]{0.5f, 1.0f, -0.25f}, "内容", Map.of());

            verify(vectorMapper).updateEmbedding("p1", "[0.5,1.0,-0.25]");
        }

        @Test
        void storeBatchShouldSkipNullOrEmptyEntries() {
            vectorStore.storeBatch(null);
            vectorStore.storeBatch(List.of());

            verifyNoInteractions(vectorMapper);
        }

        @Test
        void storeBatchShouldWriteInChunksOf500() {
            List<VectorStore.VectorEntry> entries = new ArrayList<>();
            for (int i = 0; i < 501; i++) {
                entries.add(new VectorStore.VectorEntry("p" + i, new float[]{1.0f}, "内容", "kb1", "doc1", Map.of()));
            }

            vectorStore.storeBatch(entries);

            ArgumentCaptor<List<VectorUpdateItem>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorMapper, times(2)).updateEmbeddingBatch(captor.capture());
            assertThat(captor.getAllValues().get(0)).hasSize(500);
            assertThat(captor.getAllValues().get(1)).hasSize(1);
            assertThat(captor.getAllValues().get(0).get(0).getId()).isEqualTo("p0");
            assertThat(captor.getAllValues().get(0).get(0).getVector()).isEqualTo("[1.0]");
            assertThat(captor.getAllValues().get(1).get(0).getId()).isEqualTo("p500");
        }

        @Test
        void storeDocumentSummaryShouldDelegate() {
            vectorStore.storeDocumentSummary("doc1", new float[]{0.2f}, "摘要");

            verify(vectorMapper).updateDocumentSummary("doc1", "摘要", "[0.2]");
        }
    }

    @Nested
    class 检索映射 {

        @Test
        void searchShouldMapSimilarityToVectorScoreAndMetadataTitle() {
            when(vectorMapper.searchByVector("[0.1,0.2]", "kb1", 5)).thenReturn(List.of(
                    row("p1", "doc1", "kb1", "内容1", "标题1"),
                    row("p2", "doc1", "kb1", "内容2", null)));

            List<RetrievalResult> results = vectorStore.search(new float[]{0.1f, 0.2f}, "kb1", 5);

            assertThat(results).hasSize(2);
            RetrievalResult first = results.get(0);
            assertThat(first.getParagraphId()).isEqualTo("p1");
            assertThat(first.getDocumentId()).isEqualTo("doc1");
            assertThat(first.getKnowledgeBaseId()).isEqualTo("kb1");
            assertThat(first.getContent()).isEqualTo("内容1");
            assertThat(first.getFinalScore()).isZero();
            assertThat(first.getMetadata()).containsEntry("title", "标题1");
            // 未提供 title 时 metadata 为空
            assertThat(results.get(1).getMetadata()).isEmpty();
        }

        @Test
        void searchShouldTreatMissingSimilarityAsZero() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "p1");
            when(vectorMapper.searchByVector("[1.0]", "kb1", 5)).thenReturn(List.of(row));

            List<RetrievalResult> results = vectorStore.search(new float[]{1.0f}, "kb1", 5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVectorScore()).isZero();
            assertThat(results.get(0).getFullTextScore()).isZero();
        }

        @Test
        void numberOfSimilarityValueShouldBeConvertedToDouble() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "p1");
            row.put("similarity", Float.valueOf(0.75f));
            when(vectorMapper.searchByVector("[1.0]", "kb1", 5)).thenReturn(List.of(row));

            List<RetrievalResult> results = vectorStore.search(new float[]{1.0f}, "kb1", 5);

            assertThat(results.get(0).getVectorScore()).isEqualTo(0.75);
        }

        @Test
        void fullTextSearchShouldMapRankToFullTextScore() {
            Map<String, Object> rankRow = row("p1", "doc1", "kb1", "内容1", null);
            rankRow.put("rank", 3.5);
            when(vectorMapper.searchByFullText("查询", "kb1", 5)).thenReturn(List.of(rankRow));

            List<RetrievalResult> results = vectorStore.fullTextSearch("查询", "kb1", 5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFullTextScore()).isEqualTo(3.5);
            assertThat(results.get(0).getVectorScore()).isZero();
        }

        @Test
        void searchByDocumentIdsShouldShortCircuitOnEmptyInput() {
            assertThat(vectorStore.searchByDocumentIds(new float[]{1.0f}, null, 5)).isEmpty();
            assertThat(vectorStore.searchByDocumentIds(new float[]{1.0f}, List.of(), 5)).isEmpty();

            verifyNoInteractions(vectorMapper);
        }

        @Test
        void searchByDocumentIdsShouldMapRows() {
            when(vectorMapper.searchByDocumentIds("[1.0]", List.of("doc1", "doc2"), 5)).thenReturn(List.of(
                    row("p1", "doc1", "kb1", "内容1", null)));

            List<RetrievalResult> results = vectorStore.searchByDocumentIds(new float[]{1.0f}, List.of("doc1", "doc2"), 5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getParagraphId()).isEqualTo("p1");
        }

        @Test
        void getByParagraphIdsShouldShortCircuitAndMapRowsWithZeroScores() {
            assertThat(vectorStore.getByParagraphIds(null, 10)).isEmpty();
            assertThat(vectorStore.getByParagraphIds(List.of(), 10)).isEmpty();
            verifyNoInteractions(vectorMapper);

            when(vectorMapper.selectByParagraphIds(List.of("p1"), 10))
                    .thenReturn(List.of(row("p1", "doc1", "kb1", "内容1", "标题1")));
            List<RetrievalResult> results = vectorStore.getByParagraphIds(List.of("p1"), 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVectorScore()).isZero();
            assertThat(results.get(0).getFullTextScore()).isZero();
        }

        @Test
        void searchDocumentSummariesShouldDelegate() {
            when(vectorMapper.searchSummaryDocIds("[0.3]", "kb1", 2)).thenReturn(List.of("doc1", "doc2"));

            List<String> docIds = vectorStore.searchDocumentSummaries(new float[]{0.3f}, "kb1", 2);

            assertThat(docIds).containsExactly("doc1", "doc2");
        }
    }

    @Nested
    class 删除 {

        @Test
        void deleteMethodsShouldDelegateToMapper() {
            vectorStore.deleteByKnowledgeBase("kb1");
            vectorStore.deleteByDocument("doc1");

            verify(vectorMapper).clearEmbeddingByKb("kb1");
            verify(vectorMapper).clearEmbeddingByDocument("doc1");
        }
    }
}
