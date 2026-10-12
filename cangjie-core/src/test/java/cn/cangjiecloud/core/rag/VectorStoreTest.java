package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VectorStore} 单元测试。
 * <p>
 * 接口上的抽象方法是实现方必须提供的存储/检索能力，多知识库与文档范围重载则是 default 兼容
 * 兼底（逐库循环），实现方可覆盖为单条 IN 查询。这里同时候验：
 * <ul>
 *   <li>抽象契约与 default 方法形状</li>
 *   <li>default 兼底的语义（拼接顺序、文档范围过滤、摘要去重）</li>
 *   <li>嵌套 record {@link VectorStore.VectorEntry} 的访问器 / equals / hashCode / toString</li>
 * </ul>
 * 并用 Mockito 验证调用契约（含异常传播）。
 */
class VectorStoreTest {

    private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

    private static VectorStore.VectorEntry entry(String paragraphId, float[] embedding) {
        return new VectorStore.VectorEntry(paragraphId, embedding, "content-of-" + paragraphId,
                "kb-1", "doc-1", Map.of("k", "v"));
    }

    @Nested
    class VectorEntryRecord {

        @Test
        void accessorsShouldReturnConstructorArguments() {
            VectorStore.VectorEntry e = entry("p-1", VECTOR);

            assertThat(e.paragraphId()).isEqualTo("p-1");
            assertThat(e.embedding()).isSameAs(VECTOR);
            assertThat(e.content()).isEqualTo("content-of-p-1");
            assertThat(e.knowledgeBaseId()).isEqualTo("kb-1");
            assertThat(e.documentId()).isEqualTo("doc-1");
            assertThat(e.metadata()).containsEntry("k", "v");
        }

        @Test
        void recordShouldDeclareSixComponentsInOrder() {
            assertThat(VectorStore.VectorEntry.class.isRecord()).isTrue();
            assertThat(Arrays.stream(VectorStore.VectorEntry.class.getRecordComponents())
                    .map(RecordComponent::getName).toList())
                    .containsExactly("paragraphId", "embedding", "content",
                            "knowledgeBaseId", "documentId", "metadata");
        }

        @Test
        void equalsShouldTreatSameArrayInstanceAsEqual() {
            assertThat(entry("p-1", VECTOR)).isEqualTo(entry("p-1", VECTOR));
            assertThat(entry("p-1", VECTOR)).hasSameHashCodeAs(entry("p-1", VECTOR));
        }

        @Test
        void equalsShouldCompareArrayComponentsByIdentityNotContent() {
            VectorStore.VectorEntry a = entry("p-1", new float[]{0.1f, 0.2f, 0.3f});
            VectorStore.VectorEntry b = entry("p-1", new float[]{0.1f, 0.2f, 0.3f});

            // record 对数组组件使用引用比较：内容相同但实例不同 => 不相等
            assertThat(a).isNotEqualTo(b);
            assertThat(a.embedding()).containsExactly(b.embedding());
        }

        @Test
        void equalsShouldDifferWhenAnyNonArrayComponentDiffers() {
            assertThat(entry("p-1", VECTOR)).isNotEqualTo(entry("p-2", VECTOR));
            assertThat(entry("p-1", VECTOR)).isNotEqualTo(
                    new VectorStore.VectorEntry("p-1", VECTOR, "other", "kb-1", "doc-1", Map.of("k", "v")));
            assertThat(entry("p-1", VECTOR)).isNotEqualTo(
                    new VectorStore.VectorEntry("p-1", VECTOR, "content-of-p-1", "kb-2", "doc-1", Map.of("k", "v")));
            assertThat(entry("p-1", VECTOR)).isNotEqualTo(
                    new VectorStore.VectorEntry("p-1", VECTOR, "content-of-p-1", "kb-1", "doc-2", Map.of("k", "v")));
            assertThat(entry("p-1", VECTOR)).isNotEqualTo(
                    new VectorStore.VectorEntry("p-1", VECTOR, "content-of-p-1", "kb-1", "doc-1", Map.of()));
        }

        @Test
        void equalsShouldRejectNullAndOtherTypes() {
            VectorStore.VectorEntry e = entry("p-1", VECTOR);

            assertThat(e).isNotEqualTo(null);
            assertThat(e).isNotEqualTo("p-1");
            assertThat(e).isNotEqualTo(e.paragraphId());
        }

        @Test
        void equalsShouldBeReflexive() {
            VectorStore.VectorEntry e = entry("p-1", VECTOR);

            assertThat(e).isEqualTo(e);
        }

        @Test
        void canonicalConstructorShouldAcceptNullComponents() {
            VectorStore.VectorEntry e = new VectorStore.VectorEntry(null, null, null, null, null, null);

            assertThat(e.paragraphId()).isNull();
            assertThat(e.embedding()).isNull();
            assertThat(e.content()).isNull();
            assertThat(e.knowledgeBaseId()).isNull();
            assertThat(e.documentId()).isNull();
            assertThat(e.metadata()).isNull();
        }

        @Test
        void toStringShouldExposeComponentNamesAndValues() {
            VectorStore.VectorEntry e = entry("p-9", new float[]{1.0f});

            assertThat(e.toString())
                    .startsWith("VectorEntry[paragraphId=p-9")
                    .contains("embedding=")
                    .contains("content=content-of-p-9")
                    .contains("knowledgeBaseId=kb-1")
                    .contains("documentId=doc-1")
                    .endsWith("metadata={k=v}]");
        }

        @Test
        void recordShouldNotExposeSetters() {
            assertThat(Arrays.stream(VectorStore.VectorEntry.class.getDeclaredMethods())
                    .map(Method::getName))
                    .doesNotContain("setParagraphId", "setEmbedding")
                    .contains("paragraphId", "embedding", "content", "knowledgeBaseId", "documentId", "metadata");
        }
    }

    @Nested
    class StoreContract {

        @Test
        void interfaceShouldKeepAbstractContractAndDefaultMultiScopeVariants() {
            assertThat(VectorStore.class.isInterface()).isTrue();
            assertThat(Arrays.stream(VectorStore.class.getDeclaredMethods()).anyMatch(m -> !m.isDefault()))
                    .isTrue();
            // 多库 / 文档范围重载以 default 兑底，不强制所有实现方同时改签名
            assertThat(Arrays.stream(VectorStore.class.getDeclaredMethods())
                    .filter(Method::isDefault)
                    .map(Method::getName))
                    .containsOnly("search", "fullTextSearch", "searchDocumentSummaries");
        }

        @Test
        void implementationShouldRoundTripStoreAndSearch() {
            VectorStore store = mock(VectorStore.class);
            List<RetrievalResult> hits = List.of(RetrievalResult.builder().paragraphId("p-1").build());
            when(store.search(any(float[].class), anyString(), anyInt())).thenReturn(hits);

            store.store("p-1", VECTOR, "content", Map.of("k", "v"));
            store.storeBatch(List.of(entry("p-1", VECTOR), entry("p-2", VECTOR)));

            assertThat(store.search(VECTOR, "kb-1", 3)).isSameAs(hits);
            verify(store).store("p-1", VECTOR, "content", Map.of("k", "v"));
            verify(store).storeBatch(anyList());
        }

        @Test
        void deleteMethodsShouldPropagateImplementationFailure() {
            VectorStore store = mock(VectorStore.class);
            doThrow(new IllegalStateException("db down")).when(store).deleteByKnowledgeBase("kb-1");
            doThrow(new IllegalStateException("db down")).when(store).deleteByDocument("doc-1");

            assertThatThrownBy(() -> store.deleteByKnowledgeBase("kb-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db down");
            assertThatThrownBy(() -> store.deleteByDocument("doc-1"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void twoStageMethodsShouldDelegateToImplementation() {
            VectorStore store = mock(VectorStore.class);
            when(store.searchDocumentSummaries(any(float[].class), eq("kb-1"), anyInt()))
                    .thenReturn(List.of("doc-2", "doc-1"));
            when(store.searchByDocumentIds(any(float[].class), anyList(), anyInt()))
                    .thenReturn(List.of(RetrievalResult.builder().documentId("doc-2").build()));

            store.storeDocumentSummary("doc-2", VECTOR, "summary");

            List<String> docs = store.searchDocumentSummaries(VECTOR, "kb-1", 2);
            assertThat(docs).containsExactly("doc-2", "doc-1");
            assertThat(store.searchByDocumentIds(VECTOR, docs, 5)).singleElement()
                    .extracting(RetrievalResult::getDocumentId).isEqualTo("doc-2");
            verify(store).storeDocumentSummary("doc-2", VECTOR, "summary");
        }

        @Test
        void fullTextSearchShouldReturnEmptyListWhenNoMatch() {
            VectorStore store = mock(VectorStore.class);
            when(store.fullTextSearch(anyString(), anyString(), anyInt())).thenReturn(List.of());

            assertThat(store.fullTextSearch("no-hit", "kb-1", 10)).isEmpty();
        }
    }

    @Nested
    class MultiScopeDefaults {

        /** 只实现抽象方法的极简存储，用于观察 default 重载如何回落到单库方法 */
        private static final class AbstractOnlyStore implements VectorStore {

            private final Map<String, List<RetrievalResult>> vector = new LinkedHashMap<>();
            private final Map<String, List<RetrievalResult>> fullText = new LinkedHashMap<>();
            private final Map<String, List<String>> summaries = new LinkedHashMap<>();
            private final List<String> calls = new ArrayList<>();

            @Override
            public void store(String paragraphId, float[] embedding, String content, Map<String, Object> metadata) {
            }

            @Override
            public void storeBatch(List<VectorEntry> entries) {
            }

            @Override
            public List<RetrievalResult> search(float[] queryEmbedding, String knowledgeBaseId, int topK) {
                calls.add("vector:" + knowledgeBaseId);
                return vector.getOrDefault(knowledgeBaseId, List.of());
            }

            @Override
            public List<RetrievalResult> fullTextSearch(String query, String knowledgeBaseId, int topK) {
                calls.add("fulltext:" + knowledgeBaseId);
                return fullText.getOrDefault(knowledgeBaseId, List.of());
            }

            @Override
            public void deleteByKnowledgeBase(String knowledgeBaseId) {
            }

            @Override
            public void deleteByDocument(String documentId) {
            }

            @Override
            public void storeDocumentSummary(String documentId, float[] embedding, String summary) {
            }

            @Override
            public List<String> searchDocumentSummaries(float[] queryEmbedding, String knowledgeBaseId, int topK) {
                calls.add("summary:" + knowledgeBaseId);
                return summaries.getOrDefault(knowledgeBaseId, List.of());
            }

            @Override
            public List<RetrievalResult> searchByDocumentIds(float[] queryEmbedding, List<String> documentIds, int topK) {
                return List.of();
            }
        }

        private static RetrievalResult hit(String paragraphId, String documentId) {
            return RetrievalResult.builder().paragraphId(paragraphId).documentId(documentId).build();
        }

        private static RetrievalResult vectorHit(String paragraphId, String documentId, double score) {
            return hit(paragraphId, documentId).toBuilder().vectorScore(score).build();
        }

        private static RetrievalResult textHit(String paragraphId, String documentId, double score) {
            return hit(paragraphId, documentId).toBuilder().fullTextScore(score).build();
        }

        @Test
        void vectorDefaultShouldRankAcrossKnowledgeBasesInsteadOfPerLibraryOrder() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            // 每个库各自排好序，简单拼接会让 kb-1 的低分段落压在 kb-2 的高分段落前面
            store.vector.put("kb-1", List.of(vectorHit("p1", "doc1", 0.30)));
            store.vector.put("kb-2", List.of(vectorHit("p2", "doc2", 0.95), vectorHit("p3", "doc3", 0.60)));

            assertThat(store.search(VECTOR, List.of("kb-1", "kb-2"), 5))
                    .extracting(RetrievalResult::getParagraphId)
                    .containsExactly("p2", "p3", "p1");
        }

        @Test
        void vectorDefaultShouldDedupeParagraphAndTrimToTopK() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.vector.put("kb-1", List.of(vectorHit("shared", "doc1", 0.40)));
            store.vector.put("kb-2", List.of(vectorHit("shared", "doc2", 0.80), vectorHit("p3", "doc3", 0.20)));

            List<RetrievalResult> merged = store.search(VECTOR, List.of("kb-1", "kb-2"), 1);

            // 同一段落落在两个库里只留高分那条，超出 topK 的裁掉
            assertThat(merged).singleElement().extracting(RetrievalResult::getParagraphId).isEqualTo("shared");
            assertThat(merged.get(0).getVectorScore()).isEqualTo(0.80);
        }

        @Test
        void fullTextDefaultShouldRankByScoreAcrossKnowledgeBases() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.fullText.put("kb-1", List.of(textHit("p1", "doc1", 1.0)));
            store.fullText.put("kb-2", List.of(textHit("p2", "doc2", 9.0), textHit("p3", "doc3", 5.0)));

            assertThat(store.fullTextSearch("q", List.of("kb-1", "kb-2"), null, 2))
                    .extracting(RetrievalResult::getParagraphId)
                    .containsExactly("p2", "p3");
        }

        @Test
        void vectorDefaultShouldWalkKnowledgeBasesInScopeOrder() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.vector.put("kb-1", List.of(hit("p1", "doc1")));
            store.vector.put("kb-2", List.of(hit("p2", "doc2"), hit("p3", "doc3")));

            List<RetrievalResult> merged = store.search(VECTOR, List.of("kb-1", "kb-2"), 5);

            assertThat(merged).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2", "p3");
            assertThat(store.calls).containsExactly("vector:kb-1", "vector:kb-2");
        }

        @Test
        void fullTextDefaultShouldRestrictToGivenDocuments() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.fullText.put("kb-1", List.of(hit("p1", "doc1"), hit("pX", "docX")));
            store.fullText.put("kb-2", List.of(hit("p2", "doc2")));

            List<RetrievalResult> scoped = store.fullTextSearch("q", List.of("kb-1", "kb-2"),
                    List.of("doc1", "doc2"), 5);

            // two_stage 的候选文档约束在 default 兼底下依然成立，只是多了一次内存过滤
            assertThat(scoped).extracting(RetrievalResult::getParagraphId).containsExactly("p1", "p2");
        }

        @Test
        void fullTextDefaultShouldNotFilterWhenDocumentScopeAbsent() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.fullText.put("kb-1", List.of(hit("p1", "doc1"), hit("pX", "docX")));

            assertThat(store.fullTextSearch("q", List.of("kb-1"), null, 5)).hasSize(2);
            assertThat(store.fullTextSearch("q", List.of("kb-1"), List.of(), 5)).hasSize(2);
        }

        @Test
        void summaryDefaultShouldMergeAcrossKnowledgeBasesWithoutDuplicates() {
            AbstractOnlyStore store = new AbstractOnlyStore();
            store.summaries.put("kb-1", List.of("doc1", "doc2"));
            store.summaries.put("kb-2", List.of("doc2", "doc3"));

            List<String> docs = store.searchDocumentSummaries(VECTOR, List.of("kb-1", "kb-2"), 3);

            assertThat(docs).containsExactly("doc1", "doc2", "doc3");
            assertThat(store.calls).containsExactly("summary:kb-1", "summary:kb-2");
        }

        @Test
        void defaultOverloadsShouldAcceptEmptyScope() {
            AbstractOnlyStore store = new AbstractOnlyStore();

            assertThat(store.search(VECTOR, List.of(), 3)).isEmpty();
            assertThat(store.fullTextSearch("q", List.of(), (Collection<String>) null, 3)).isEmpty();
            assertThat(store.searchDocumentSummaries(VECTOR, List.of(), 3)).isEmpty();
            assertThat(store.calls).isEmpty();
        }
    }
}
