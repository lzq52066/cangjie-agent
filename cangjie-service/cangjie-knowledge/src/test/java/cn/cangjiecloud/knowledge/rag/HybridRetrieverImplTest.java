package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RerankerFactory;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HybridRetrieverImpl} 单元测试。
 * <p>
 * 覆盖：入参校验、加权融合、阈值过滤、topK 截断、异常降级、重排序与 two_stage 两级检索。
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrieverImplTest {

    private static final float[] QUERY_EMBEDDING = {0.1f, 0.2f};

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private RerankerFactory rerankerFactory;

    private HybridRetrieverImpl retriever;

    @BeforeEach
    void setUp() {
        retriever = new HybridRetrieverImpl(vectorStore, embeddingProvider, rerankerFactory);
        ReflectionTestUtils.setField(retriever, "vectorWeight", 0.7);
        ReflectionTestUtils.setField(retriever, "fullTextWeight", 0.3);
        ReflectionTestUtils.setField(retriever, "rerankEnabled", false);
        ReflectionTestUtils.setField(retriever, "rerankType", "none");
    }

    private static RetrievalResult vectorResult(String paragraphId, double score) {
        return RetrievalResult.builder()
                .paragraphId(paragraphId)
                .documentId("doc-" + paragraphId)
                .content("内容-" + paragraphId)
                .vectorScore(score)
                .build();
    }

    private static RetrievalResult fullTextResult(String paragraphId, double rank) {
        return RetrievalResult.builder()
                .paragraphId(paragraphId)
                .documentId("doc-" + paragraphId)
                .content("内容-" + paragraphId)
                .fullTextScore(rank)
                .build();
    }

    @Test
    void invalidInputsShouldReturnEmptyWithoutTouchingDependencies() {
        assertThat(retriever.retrieve(null, List.of("kb1"), 3, 0.0)).isEmpty();
        assertThat(retriever.retrieve("  ", List.of("kb1"), 3, 0.0)).isEmpty();
        assertThat(retriever.retrieve("q", List.of(), 3, 0.0)).isEmpty();
        assertThat(retriever.retrieve("q", (List<String>) null, 3, 0.0)).isEmpty();
    }

    @Nested
    class 简单模式 {

        @Test
        void shouldFuseVectorAndFullTextScoresByWeight() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch("q", "kb1", 6))
                    .thenReturn(List.of(fullTextResult("p2", 3.0), fullTextResult("p1", 6.0)));

            List<RetrievalResult> results = retriever.retrieve("q", "kb1", 2, 0.0);

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            // p1 = 0.7*0.9 + 0.3*(6/6) = 0.93；p2 = 0.7*0 + 0.3*(3/6) = 0.15
            assertThat(results.get(0).getFinalScore()).isCloseTo(0.93, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(results.get(1).getFinalScore()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9));
            // 融合仅写 finalScore，来源对象保持各通道原始分数（p1 保留的是向量通道对象）
            assertThat(results.get(0).getVectorScore()).isEqualTo(0.9);
            assertThat(results.get(0).getFullTextScore()).isZero();
            assertThat(results.get(1).getVectorScore()).isZero();
            assertThat(results.get(1).getFullTextScore()).isEqualTo(3.0);
        }

        @Test
        void vectorFailureShouldFallBackToFullTextOnly() {
            when(embeddingProvider.embed("q")).thenThrow(new RuntimeException("embedding down"));
            when(vectorStore.fullTextSearch("q", "kb1", 6))
                    .thenReturn(List.of(fullTextResult("p1", 2.0)));

            List<RetrievalResult> results = retriever.retrieve("q", "kb1", 2, 0.0);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFinalScore()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        void fullTextFailureShouldFallBackToVectorOnly() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch("q", "kb1", 6)).thenThrow(new RuntimeException("fulltext down"));

            List<RetrievalResult> results = retriever.retrieve("q", "kb1", 2, 0.0);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFinalScore()).isCloseTo(0.63, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        void similarityThresholdShouldFilterLowScoreResults() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 15))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch("q", "kb1", 15))
                    .thenReturn(List.of(fullTextResult("p2", 3.0), fullTextResult("p1", 6.0)));

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 5, 0.5);

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1");
        }

        @Test
        void topKShouldLimitResultSize() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6)).thenReturn(List.of(
                    vectorResult("p1", 0.9), vectorResult("p2", 0.8), vectorResult("p3", 0.7)));
            when(vectorStore.fullTextSearch("q", "kb1", 6)).thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0);

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
        }

        @Test
        void rerankShouldBeAppliedWhenEnabled() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");
            Reranker reranker = org.mockito.Mockito.mock(Reranker.class);

            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6)).thenReturn(List.of(
                    vectorResult("p1", 0.9), vectorResult("p2", 0.8), vectorResult("p3", 0.7)));
            when(vectorStore.fullTextSearch("q", "kb1", 6)).thenReturn(List.of());
            when(rerankerFactory.get("bge")).thenReturn(reranker);
            when(reranker.rerank(eq("q"), anyList(), eq(2)))
                    .thenReturn(List.of(vectorResult("p3", 0.0)));

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0);

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p3");
            verify(reranker).rerank(eq("q"), anyList(), eq(2));
        }

        @Test
        void rerankShouldBeSkippedWhenCandidatesNotExceedingTopK() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");

            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch("q", "kb1", 6)).thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0);

            assertThat(results).hasSize(1);
            verify(rerankerFactory, never()).get(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    class 两级检索 {

        @Test
        void twoStageShouldRestrictParagraphSearchToCandidateDocuments() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(QUERY_EMBEDDING, "kb1", 2))
                    .thenReturn(List.of("doc1", "doc2"));
            when(vectorStore.searchByDocumentIds(eq(QUERY_EMBEDDING), anyList(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9), vectorResult("p2", 0.8)));
            // 全文结果：p1 属于候选文档 doc1（保留），p3 属于候选范围外文档（应被过滤）
            RetrievalResult fullTextInScope = RetrievalResult.builder()
                    .paragraphId("p1").documentId("doc1").content("内容-p1").fullTextScore(6.0).build();
            RetrievalResult fullTextOutOfScope = RetrievalResult.builder()
                    .paragraphId("p3").documentId("doc3").content("内容-p3").fullTextScore(5.0).build();
            when(vectorStore.fullTextSearch("q", "kb1", 6))
                    .thenReturn(List.of(fullTextInScope, fullTextOutOfScope));

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0, "two_stage");

            // p3 属于候选范围外的文档，被过滤
            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            ArgumentCaptor<List<String>> docIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).searchByDocumentIds(eq(QUERY_EMBEDDING), docIdsCaptor.capture(), eq(6));
            assertThat(docIdsCaptor.getValue()).containsExactly("doc1", "doc2");
        }

        @Test
        void twoStageShouldFallbackToSimpleWhenNoCandidateDocuments() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(QUERY_EMBEDDING, "kb1", 2)).thenReturn(List.of());
            when(vectorStore.search(QUERY_EMBEDDING, "kb1", 6))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch("q", "kb1", 6)).thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0, "two_stage");

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1");
            verify(vectorStore, never()).searchByDocumentIds(org.mockito.ArgumentMatchers.any(), anyList(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        void twoStageShouldReturnEmptyWhenEmbeddingFails() {
            when(embeddingProvider.embed("q")).thenThrow(new RuntimeException("embedding down"));

            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2, 0.0, "two_stage");

            assertThat(results).isEmpty();
            verify(vectorStore, never()).searchDocumentSummaries(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
        }
    }
}
