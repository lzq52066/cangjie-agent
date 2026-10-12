package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RerankerFactory;
import cn.cangjiecloud.core.rag.RetrievalOptions;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HybridRetrieverImpl} 单元测试。
 * <p>
 * 覆盖：入参校验、RRF 与加权融合、阈值只裁向量通道、topK 截断、异常降级、重排序触发条件、
 * two_stage 候选文档下推，以及按知识库配置解析检索模式与本地缓存。
 * <p>
 * 默认融合是 RRF，所以分数断言写的是名次和 {@code 1/(60+rank)}：名次比分数稳，
 * 换成 weighted 的用例单独标出来。
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrieverImplTest {

    private static final float[] QUERY_EMBEDDING = {0.1f, 0.2f};

    /** RRF k=60 时第 1、2 名的贡献分 */
    private static final double RANK_1 = 1.0 / 61;
    private static final double RANK_2 = 1.0 / 62;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private RerankerFactory rerankerFactory;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private HybridRetrieverImpl retriever;

    @BeforeEach
    void setUp() {
        retriever = new HybridRetrieverImpl(vectorStore, embeddingProvider, rerankerFactory, knowledgeBaseMapper);
        ReflectionTestUtils.setField(retriever, "vectorWeight", 0.7);
        ReflectionTestUtils.setField(retriever, "fullTextWeight", 0.3);
        ReflectionTestUtils.setField(retriever, "fusion", "rrf");
        ReflectionTestUtils.setField(retriever, "rrfK", 60);
        ReflectionTestUtils.setField(retriever, "rerankEnabled", false);
        ReflectionTestUtils.setField(retriever, "rerankType", "none");
        ReflectionTestUtils.setField(retriever, "candidateMultiplier", 3);
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

    /** 显式指定 simple 模式，避免走到知识库配置解析（那部分单独测） */
    private static RetrievalOptions simpleOptions(int topK, double threshold) {
        return RetrievalOptions.builder()
                .knowledgeBaseIds(List.of("kb1")).topK(topK)
                .similarityThreshold(threshold).searchMode(RetrievalOptions.MODE_SIMPLE)
                .build();
    }

    private void stubEmbed() {
        when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
    }

    private static double finalScoreOf(List<RetrievalResult> results, String paragraphId) {
        return results.stream().filter(r -> paragraphId.equals(r.getParagraphId())).findAny()
                .orElseThrow().getFinalScore();
    }

    @Test
    void invalidInputsShouldReturnEmptyWithoutTouchingDependencies() {
        assertThat(retriever.retrieve(null, simpleOptions(3, 0.0))).isEmpty();
        assertThat(retriever.retrieve("  ", simpleOptions(3, 0.0))).isEmpty();
        assertThat(retriever.retrieve("q", RetrievalOptions.builder().topK(3).build())).isEmpty();
        assertThat(retriever.retrieve("q",
                RetrievalOptions.builder().knowledgeBaseIds(Arrays.asList(" ", null)).topK(3).build())).isEmpty();
        assertThat(retriever.retrieve("q", (RetrievalOptions) null)).isEmpty();
        verifyNoStoreAccess();
    }

    private void verifyNoStoreAccess() {
        verify(vectorStore, never()).search(any(), anyString(), anyInt());
        verify(vectorStore, never()).search(any(), anyCollection(), anyInt());
    }

    @Nested
    class 简单模式 {

        @Test
        void rrfShouldRankByReciprocalRankAcrossChannels() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(9)))
                    .thenReturn(List.of(vectorResult("p1", 0.9), vectorResult("p2", 0.8)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(9)))
                    .thenReturn(List.of(fullTextResult("p2", 6.0), fullTextResult("p3", 3.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(3, 0.0));

            // p2 两路都命中（1/61 + 1/62），排在只命中单路第一名的 p1 之前
            assertThat(results).extracting(RetrievalResult::getParagraphId)
                    .containsExactly("p2", "p1", "p3");
            assertThat(results.get(0).getFinalScore()).isCloseTo(RANK_1 + RANK_2, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(results.get(1).getFinalScore()).isCloseTo(RANK_1, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(results.get(2).getFinalScore()).isCloseTo(RANK_2, org.assertj.core.data.Offset.offset(1e-12));
        }

        @Test
        void rrfShouldBeIndependentOfChannelScoreScale() {
            stubEmbed();
            // pgroonga 分无上界：这里给一个远大于向量分的量级，RRF 排序不受影响
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(9)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(9)))
                    .thenReturn(List.of(fullTextResult("p2", 9800.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(3, 0.0));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            assertThat(finalScoreOf(results, "p1")).isEqualTo(finalScoreOf(results, "p2"));
        }

        @Test
        void weightedFusionShouldStayAvailableAndNormalizeFullTextByBatchMax() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6)))
                    .thenReturn(List.of(fullTextResult("p2", 3.0), fullTextResult("p1", 6.0)));

            List<RetrievalResult> results = retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).topK(2).similarityThreshold(0.0)
                    .searchMode(RetrievalOptions.MODE_SIMPLE).fusion(RetrievalOptions.FUSION_WEIGHTED)
                    .build());

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            // p1 = 0.7*0.9 + 0.3*(6/6) = 0.93；p2 = 0.7*0 + 0.3*(3/6) = 0.15
            assertThat(results.get(0).getFinalScore()).isCloseTo(0.93, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(results.get(1).getFinalScore()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        void fusionShouldNotMutateSourceResults() {
            RetrievalResult p1 = vectorResult("p1", 0.9);
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(p1));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6)))
                    .thenReturn(List.of(fullTextResult("p1", 6.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            // 融合写的是副本，来源对象的通道分不被覆写（上层若缓存结果不会串味）
            assertThat(p1.getFinalScore()).isZero();
            assertThat(p1.getFullTextScore()).isZero();
            assertThat(results.get(0).getFinalScore()).isCloseTo(RANK_1 * 2, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(results.get(0).getVectorScore()).isEqualTo(0.9);
            assertThat(results.get(0).getFullTextScore()).isEqualTo(6.0);
        }

        @Test
        void sameParagraphFromMultipleKnowledgeBasesShouldKeepHigherChannelScore() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(9)))
                    .thenReturn(List.of(vectorResult("p1", 0.5), vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(9)))
                    .thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(3, 0.0));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVectorScore()).isEqualTo(0.9);
        }

        @Test
        void vectorFailureShouldFallBackToFullTextOnly() {
            when(embeddingProvider.embed("q")).thenThrow(new RuntimeException("embedding down"));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6)))
                    .thenReturn(List.of(fullTextResult("p1", 2.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFinalScore()).isCloseTo(RANK_1, org.assertj.core.data.Offset.offset(1e-12));
        }

        @Test
        void fullTextFailureShouldFallBackToVectorOnly() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6)))
                    .thenThrow(new RuntimeException("fulltext down"));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFinalScore()).isCloseTo(RANK_1, org.assertj.core.data.Offset.offset(1e-12));
        }

        @Test
        void thresholdShouldCutVectorChannelOnlyAndKeepFullTextHits() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(15)))
                    .thenReturn(List.of(vectorResult("p1", 0.9), vectorResult("p2", 0.2)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(15)))
                    .thenReturn(List.of(fullTextResult("p2", 6.0), fullTextResult("p3", 3.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(5, 0.5));

            // p2 向量分 0.2 低于阈值，向量名次不再计分，但它由全文命中所以仍被召回
            assertThat(results).extracting(RetrievalResult::getParagraphId)
                    .containsExactly("p1", "p2", "p3");
            assertThat(finalScoreOf(results, "p2")).isCloseTo(RANK_1, org.assertj.core.data.Offset.offset(1e-12));
            // p1 与 p2 同为单路第 1 名，同分时按向量分高者优先；p2 的向量命中被阈值裁掉，
            // 融合行不再携带那个低于阈值的向量分（向量通道本次没贡献）
            assertThat(results.get(0).getParagraphId()).isEqualTo("p1");
            assertThat(results.get(1).getVectorScore()).isZero();
            assertThat(results.get(1).getFullTextScore()).isEqualTo(6.0);
        }

        @Test
        void thresholdShouldNotBeAppliedToFusedScore() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(15)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(15)))
                    .thenReturn(List.of(fullTextResult("p2", 6.0)));

            // RRF 分只有 0.016 量级，若拿 threshold 比融合分会把全文命中全删掉
            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(5, 0.8));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
        }

        @Test
        void topKShouldLimitResultSizeAndExpandCandidateWindow() {
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of(
                    vectorResult("p1", 0.9), vectorResult("p2", 0.8), vectorResult("p3", 0.7)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            // candidateK = topK * candidateMultiplier
            verify(vectorStore).search(eq(QUERY_EMBEDDING), anyCollection(), eq(6));
        }

        @Test
        void candidateMultiplierShouldBeOverridablePerRequest() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(2))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(2))).thenReturn(List.of());

            retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).topK(2).candidateMultiplier(1)
                    .searchMode(RetrievalOptions.MODE_SIMPLE).build());

            verify(vectorStore).search(eq(QUERY_EMBEDDING), anyCollection(), eq(2));
        }

        @Test
        void missingTopKShouldFallBackToDefaultWindow() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(15))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(15))).thenReturn(List.of());

            retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).searchMode(RetrievalOptions.MODE_SIMPLE).build());

            verify(vectorStore).search(eq(QUERY_EMBEDDING), anyCollection(), eq(15));
        }

        @Test
        void multiKnowledgeBaseShouldQueryOnceWithWholeScope() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(Arrays.asList("kb1", "kb2", "kb1", null, " ")).topK(2)
                    .searchMode(RetrievalOptions.MODE_SIMPLE).build());

            // 一条 IN 查询：不是逐库循环后拼接（拼接会让每个库都从第 1 名开始，名次错乱）
            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(vectorStore).search(eq(QUERY_EMBEDDING), captor.capture(), eq(6));
            assertThat(captor.getValue()).containsExactly("kb1", "kb2");
            verify(vectorStore, never()).search(eq(QUERY_EMBEDDING), eq("kb1"), anyInt());
        }

        @Test
        void rerankShouldBeAppliedWhenEnabled() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");
            Reranker reranker = mock(Reranker.class);

            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of(
                    vectorResult("p1", 0.9), vectorResult("p2", 0.8), vectorResult("p3", 0.7)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());
            when(rerankerFactory.get("bge")).thenReturn(reranker);
            when(reranker.rerank(eq("q"), anyList(), eq(2)))
                    .thenReturn(List.of(vectorResult("p3", 0.0)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p3");
            verify(reranker).rerank(eq("q"), anyList(), eq(2));
        }

        @Test
        void rerankShouldStillRunWhenCandidatesDoNotExceedTopK() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");
            Reranker reranker = mock(Reranker.class);

            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());
            when(rerankerFactory.get("bge")).thenReturn(reranker);
            when(reranker.rerank(eq("q"), anyList(), eq(2)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            // 只召回到 1 条（1 < topK=2）恰恰是最需要精排的情况，旧实现按 size>topK 跳过
            assertThat(results).hasSize(1);
            verify(reranker).rerank(eq("q"), anyList(), eq(2));
        }

        @Test
        void requestRerankTypeShouldOverrideGlobalSwitch() {
            Reranker reranker = mock(Reranker.class);
            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());
            when(rerankerFactory.get("cohere")).thenReturn(reranker);
            when(reranker.rerank(eq("q"), anyList(), eq(2))).thenReturn(List.of(vectorResult("p1", 0.9)));

            List<RetrievalResult> results = retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).topK(2)
                    .searchMode(RetrievalOptions.MODE_SIMPLE).rerankType("cohere").build());

            // 全局 rerank.enabled=false，但评测可以把生产重排口径固化进请求
            assertThat(results).hasSize(1);
            verify(rerankerFactory).get("cohere");
        }

        @Test
        void rerankNoneInRequestShouldOverrideGlobalEnabled() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");

            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).topK(2)
                    .searchMode(RetrievalOptions.MODE_SIMPLE).rerankType("none").build());

            verify(rerankerFactory, never()).get(anyString());
        }

        @Test
        void rerankFailureShouldFallBackToFusionOrder() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");

            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of(
                    vectorResult("p1", 0.9), vectorResult("p2", 0.8)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());
            when(rerankerFactory.get("bge")).thenThrow(new RuntimeException("reranker down"));

            List<RetrievalResult> results = retriever.retrieve("q", simpleOptions(2, 0.0));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
        }

        @Test
        void nullRerankReturnShouldFallBackToFusedList() {
            ReflectionTestUtils.setField(retriever, "rerankEnabled", true);
            ReflectionTestUtils.setField(retriever, "rerankType", "bge");
            Reranker reranker = mock(Reranker.class);

            stubEmbed();
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());
            when(rerankerFactory.get("bge")).thenReturn(reranker);
            when(reranker.rerank(eq("q"), anyList(), eq(2))).thenReturn(null);

            assertThat(retriever.retrieve("q", simpleOptions(2, 0.0))).hasSize(1);
        }
    }

    @Nested
    class 两级检索 {

        private RetrievalOptions twoStageOptions(int topK) {
            return RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1")).topK(topK)
                    .searchMode(RetrievalOptions.MODE_TWO_STAGE).build();
        }

        @Test
        void candidateDocumentsShouldBePushedDownToBothChannels() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenReturn(List.of("doc1", "doc2"));
            when(vectorStore.searchByDocumentIds(eq(QUERY_EMBEDDING), anyList(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9), vectorResult("p2", 0.8)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), anyCollection(), eq(6)))
                    .thenReturn(List.of(fullTextResult("p1", 6.0)));

            List<RetrievalResult> results = retriever.retrieve("q", twoStageOptions(2));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
            ArgumentCaptor<List<String>> docIds = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).searchByDocumentIds(eq(QUERY_EMBEDDING), docIds.capture(), eq(6));
            assertThat(docIds.getValue()).containsExactly("doc1", "doc2");
            // 全文通道同样带候选文档约束，而不是全库检索后在内存里 filter
            ArgumentCaptor<Collection<String>> scoped = ArgumentCaptor.forClass(Collection.class);
            verify(vectorStore).fullTextSearch(eq("q"), anyCollection(), scoped.capture(), eq(6));
            assertThat(scoped.getValue()).containsExactly("doc1", "doc2");
            verify(vectorStore, never()).search(any(), anyCollection(), anyInt());
        }

        @Test
        void summaryScopeShouldCoverAllKnowledgeBases() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenReturn(List.of("doc1"));
            when(vectorStore.searchByDocumentIds(eq(QUERY_EMBEDDING), anyList(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), anyCollection(), eq(6)))
                    .thenReturn(List.of());

            retriever.retrieve("q", RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb1", "kb2")).topK(2)
                    .searchMode(RetrievalOptions.MODE_TWO_STAGE).build());

            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(vectorStore).searchDocumentSummaries(eq(QUERY_EMBEDDING), captor.capture(), eq(2));
            assertThat(captor.getValue()).containsExactly("kb1", "kb2");
        }

        @Test
        void twoStageShouldFallbackToSimpleWhenNoCandidateDocuments() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenReturn(List.of());
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            List<RetrievalResult> results = retriever.retrieve("q", twoStageOptions(2));

            assertThat(results).extracting(RetrievalResult::getParagraphId).containsExactly("p1");
            verify(vectorStore, never()).searchByDocumentIds(any(), anyList(), anyInt());
        }

        @Test
        void twoStageShouldFallbackToSimpleWhenSummarySearchFails() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenThrow(new RuntimeException("summary index missing"));
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            assertThat(retriever.retrieve("q", twoStageOptions(2))).hasSize(1);
        }

        @Test
        void twoStageShouldReturnEmptyWhenEmbeddingFails() {
            when(embeddingProvider.embed("q")).thenThrow(new RuntimeException("embedding down"));

            List<RetrievalResult> results = retriever.retrieve("q", twoStageOptions(2));

            assertThat(results).isEmpty();
            verify(vectorStore, never()).searchDocumentSummaries(any(), anyCollection(), anyInt());
        }

        @Test
        void twoStageShouldKeepWorkingWhenParagraphVectorFails() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenReturn(List.of("doc1"));
            when(vectorStore.searchByDocumentIds(eq(QUERY_EMBEDDING), anyList(), eq(6)))
                    .thenThrow(new RuntimeException("vector node down"));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), anyCollection(), eq(6)))
                    .thenReturn(List.of(fullTextResult("p1", 6.0)));

            assertThat(retriever.retrieve("q", twoStageOptions(2)))
                    .extracting(RetrievalResult::getParagraphId).containsExactly("p1");
        }
    }

    @Nested
    class 检索模式解析 {

        private void kb(String searchMode) {
            KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
            entity.setSearchMode(searchMode);
            when(knowledgeBaseMapper.selectById(anyString())).thenReturn(entity);
        }

        @Test
        void shouldFollowKnowledgeBaseConfigWhenCallerOmitsMode() {
            kb("two_stage");
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2)))
                    .thenReturn(List.of("doc1"));
            when(vectorStore.searchByDocumentIds(eq(QUERY_EMBEDDING), anyList(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), anyCollection(), eq(6)))
                    .thenReturn(List.of());

            // 走 3 参兼容入口：改造前这里永远硬编码 simple，知识库上的模式从没生效过
            List<RetrievalResult> results = retriever.retrieve("q", List.of("kb1"), 2);

            assertThat(results).hasSize(1);
            verify(vectorStore).searchDocumentSummaries(eq(QUERY_EMBEDDING), anyCollection(), eq(2));
        }

        @Test
        void mixedKnowledgeBaseModesShouldResolveToSimple() {
            KnowledgeBaseEntity twoStage = new KnowledgeBaseEntity();
            twoStage.setSearchMode("two_stage");
            KnowledgeBaseEntity simple = new KnowledgeBaseEntity();
            simple.setSearchMode("simple");
            when(knowledgeBaseMapper.selectById("kb1")).thenReturn(twoStage);
            when(knowledgeBaseMapper.selectById("kb2")).thenReturn(simple);
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            retriever.retrieve("q", List.of("kb1", "kb2"), 2);

            // 裁候选文档的配置拿去裁另一个库会直接漏召回，混用一律退回段落级
            verify(vectorStore, never()).searchDocumentSummaries(any(), anyCollection(), anyInt());
        }

        @Test
        void missingKnowledgeBaseRowShouldResolveToSimple() {
            when(knowledgeBaseMapper.selectById("kb1")).thenReturn(null);
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            assertThat(retriever.retrieve("q", List.of("kb1"), 2)).isEmpty();
            verify(vectorStore, never()).searchDocumentSummaries(any(), anyCollection(), anyInt());
        }

        @Test
        void modeLookupFailureShouldDegradeToSimpleWithoutBreakingRetrieval() {
            when(knowledgeBaseMapper.selectById("kb1")).thenThrow(new RuntimeException("db down"));
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            assertThat(retriever.retrieve("q", List.of("kb1"), 2)).hasSize(1);
        }

        @Test
        void resolvedModeShouldBeCachedAcrossRequests() {
            kb("simple");
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            retriever.retrieve("q", List.of("kb1"), 2);
            retriever.retrieve("q", List.of("kb1"), 2);

            // 检索是对话热路径，模式解析不能每次查库
            verify(knowledgeBaseMapper, times(1)).selectById("kb1");
        }

        @Test
        void explicitModeShouldSkipKnowledgeBaseLookup() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6))).thenReturn(List.of());
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            retriever.retrieve("q", simpleOptions(2, 0.0));

            verify(knowledgeBaseMapper, never()).selectById(anyString());
        }
    }

    @Nested
    class 兼容入口 {

        @Test
        void legacySignaturesShouldReachFullOptionsEntry() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(6)))
                    .thenReturn(List.of(vectorResult("p1", 0.9)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(6))).thenReturn(List.of());

            assertThat(retriever.retrieve("q", "kb1", 2)).hasSize(1);
            assertThat(retriever.retrieve("q", "kb1", 2, 0.5)).hasSize(1);
            assertThat(retriever.retrieve("q", List.of("kb1"), 2, 0.5)).hasSize(1);
            assertThat(retriever.retrieve("q", List.of("kb1"), 2, 0.5, "simple")).hasSize(1);
        }

        @Test
        void legacyThresholdShouldStillFilterVectorChannel() {
            when(embeddingProvider.embed("q")).thenReturn(QUERY_EMBEDDING);
            when(vectorStore.search(eq(QUERY_EMBEDDING), anyCollection(), eq(15)))
                    .thenReturn(List.of(vectorResult("p1", 0.2)));
            when(vectorStore.fullTextSearch(eq("q"), anyCollection(), isNull(), eq(15))).thenReturn(List.of());

            assertThat(retriever.retrieve("q", List.of("kb1"), 5, 0.5)).isEmpty();
        }
    }
}
