package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
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
 * 接口本身全部为抽象方法（无可执行逻辑），真正需要覆盖的是嵌套 record
 * {@link VectorStore.VectorEntry} 的访问器 / equals / hashCode / toString 语义，
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
        void interfaceShouldDeclareOnlyAbstractMethods() {
            assertThat(VectorStore.class.isInterface()).isTrue();
            assertThat(Arrays.stream(VectorStore.class.getDeclaredMethods()).anyMatch(m -> !m.isDefault()))
                    .isTrue();
            assertThat(Arrays.stream(VectorStore.class.getDeclaredMethods()).anyMatch(Method::isDefault))
                    .isFalse();
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
}
