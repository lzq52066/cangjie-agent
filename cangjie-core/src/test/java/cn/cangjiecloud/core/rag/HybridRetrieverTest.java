package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HybridRetriever} 单元测试。
 * <p>
 * 接口只有一个抽象方法 {@code retrieve(String, RetrievalOptions)}，其余重载都是把各自认识的
 * 参数填进口径值对象的便捷入口。这里重点验证两件事：
 * <ul>
 *   <li>入口方法不吞参数、不越权补默认值（没传的字段必须是 null，交给实现方回落配置）</li>
 *   <li>default 方法在 Mockito 代理下仍能被 callRealMethod 打通到抽象方法</li>
 * </ul>
 * 纯 JVM 测试，不加载 Spring、不连库。
 */
class HybridRetrieverTest {

    /** 记录抽象方法收到的实参，并返回预设结果。 */
    private static final class RecordingRetriever implements HybridRetriever {

        private final List<RetrievalOptions> captured = new ArrayList<>();
        private List<RetrievalResult> canned = new ArrayList<>();

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalOptions options) {
            captured.add(options);
            return canned;
        }

        RetrievalOptions last() {
            return captured.get(captured.size() - 1);
        }
    }

    private static RetrievalResult result(String paragraphId, double score) {
        return RetrievalResult.builder().paragraphId(paragraphId).finalScore(score).build();
    }

    @Nested
    class DefaultOverloads {

        @Test
        void singleKnowledgeBaseShortcutShouldWrapIdAsListAndLeaveThresholdUnset() {
            RecordingRetriever retriever = new RecordingRetriever();
            List<RetrievalResult> expected = List.of(result("p1", 0.9));
            retriever.canned = expected;

            List<RetrievalResult> actual = retriever.retrieve("问题", "kb-1", 5);

            // 3 参入口不认识阈值，必须留 null 让实现方取知识库/全局配置，而不是补 0.0 覆盖掉
            assertThat(retriever.last().getKnowledgeBaseIds()).containsExactly("kb-1");
            assertThat(retriever.last().getTopK()).isEqualTo(5);
            assertThat(retriever.last().getSimilarityThreshold()).isNull();
            assertThat(retriever.last().getSearchMode()).isNull();
            assertThat(actual).isSameAs(expected);
        }

        @Test
        void multiKnowledgeBaseShortcutShouldPassIdListThrough() {
            RecordingRetriever retriever = new RecordingRetriever();
            List<String> ids = List.of("kb-1", "kb-2");

            retriever.retrieve("q", ids, 3);

            assertThat(retriever.last().getKnowledgeBaseIds()).isEqualTo(ids);
            assertThat(retriever.last().getTopK()).isEqualTo(3);
        }

        @Test
        void singleKnowledgeBaseIdShouldBecomeOneElementList() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", 1);
            retriever.retrieve("q", List.of("kb"), 1);

            assertThat(retriever.captured).hasSize(2);
            assertThat(retriever.captured.get(0).getKnowledgeBaseIds()).containsExactly("kb");
            assertThat(retriever.captured.get(1).getKnowledgeBaseIds()).containsExactly("kb");
        }

        @Test
        void nullKnowledgeBaseIdShouldDegradeToEmptyScopeInsteadOfThrowing() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve(null, (String) null, 0);

            assertThat(retriever.last().getKnowledgeBaseIds()).isEmpty();
            assertThat(retriever.last().getTopK()).isZero();
        }

        @Test
        void nullKnowledgeBaseIdListShouldPassThroughAsNullScope() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", (List<String>) null, 2);

            assertThat(retriever.last().getKnowledgeBaseIds()).isNull();
            assertThat(retriever.last().getTopK()).isEqualTo(2);
        }

        @Test
        void shortcutShouldPropagateEmptyResultList() {
            RecordingRetriever retriever = new RecordingRetriever();
            retriever.canned = List.of();

            assertThat(retriever.retrieve("q", "kb", 10)).isEmpty();
            assertThat(retriever.retrieve("q", List.of("kb"), 10)).isEmpty();
        }

        @Test
        void shortcutShouldPropagateNullResultFromImplementation() {
            RecordingRetriever retriever = new RecordingRetriever();
            retriever.canned = null;

            assertThat(retriever.retrieve("q", "kb", 1)).isNull();
        }

        @Test
        void shortcutShouldKeepNegativeTopKAndOversizedValuesUntouched() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", -1);
            assertThat(retriever.last().getTopK()).isEqualTo(-1);

            retriever.retrieve("q", List.of("kb"), Integer.MAX_VALUE);
            assertThat(retriever.last().getTopK()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @Nested
    class ExplicitOverloads {

        @Test
        void thresholdAwareOverloadsShouldCarryThreshold() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", 4, 0.85);
            assertThat(retriever.last().getSimilarityThreshold()).isEqualTo(0.85);
            assertThat(retriever.last().getSearchMode()).isNull();

            retriever.retrieve("q", List.of("kb"), 4, 0.75);
            assertThat(retriever.last().getSimilarityThreshold()).isEqualTo(0.75);
            assertThat(retriever.last().getKnowledgeBaseIds()).containsExactly("kb");
        }

        @Test
        void zeroThresholdShouldBeCarriedAsExplicitZeroNotMissing() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", 4, 0.0);

            assertThat(retriever.last().getSimilarityThreshold()).isEqualTo(0.0);
        }

        @Test
        void searchModeOverloadShouldReceiveModeArgumentVerbatim() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", List.of("kb"), 6, 0.5, "simple");
            assertThat(retriever.last().getKnowledgeBaseIds()).containsExactly("kb");
            assertThat(retriever.last().getTopK()).isEqualTo(6);
            assertThat(retriever.last().getSimilarityThreshold()).isEqualTo(0.5);
            assertThat(retriever.last().getSearchMode()).isEqualTo("simple");

            retriever.retrieve("q", List.of("kb"), 6, 0.5, null);
            assertThat(retriever.last().getSearchMode()).isNull();
        }

        @Test
        void fullOptionsEntryShouldNotRewriteAnything() {
            RetrievalOptions options = RetrievalOptions.builder()
                    .knowledgeBaseIds(List.of("kb-1", "kb-2")).topK(8)
                    .similarityThreshold(0.42).searchMode(RetrievalOptions.MODE_TWO_STAGE)
                    .fusion(RetrievalOptions.FUSION_WEIGHTED).rerankType("bge")
                    .rrfK(30).candidateMultiplier(2).build();
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", options);

            assertThat(retriever.last()).isSameAs(options);
        }
    }

    @Nested
    class MockitoIntegration {

        @Test
        void defaultMethodShouldBeStubbableIndependentlyFromAbstraction() {
            HybridRetriever retriever = mock(HybridRetriever.class);
            List<RetrievalResult> expected = Arrays.asList(result("p1", 1.0), result("p2", 0.5));
            when(retriever.retrieve(anyString(), anyString(), anyInt())).thenReturn(expected);

            assertThat(retriever.retrieve("q", "kb", 2)).isEqualTo(expected);
            verify(retriever).retrieve("q", "kb", 2);
        }

        @Test
        void stubbedAbstractionShouldServeDefaultShortcuts() {
            HybridRetriever retriever = mock(HybridRetriever.class, invocation ->
                    invocation.getMethod().isDefault()
                            ? invocation.callRealMethod()
                            : List.of(result("p1", 0.2)));

            assertThat(retriever.retrieve("q", "kb", 3)).hasSize(1);
            verify(retriever).retrieve(anyString(), any(RetrievalOptions.class));
        }

        @Test
        void defaultShouldCarryShortcutArgumentsIntoOptions() {
            HybridRetriever retriever = mock(HybridRetriever.class, invocation ->
                    invocation.getMethod().isDefault()
                            ? invocation.callRealMethod()
                            : java.util.Collections.emptyList());

            retriever.retrieve("q", List.of("kb-1"), 5, 0.6);

            org.mockito.ArgumentCaptor<RetrievalOptions> captor =
                    org.mockito.ArgumentCaptor.forClass(RetrievalOptions.class);
            verify(retriever).retrieve(anyString(), captor.capture());
            assertThat(captor.getValue().getKnowledgeBaseIds()).containsExactly("kb-1");
            assertThat(captor.getValue().getTopK()).isEqualTo(5);
            assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.6);
        }

        @Test
        void unstubbedDefaultsOnRealMethodMockShouldDelegateToAbstraction() {
            HybridRetriever retriever = mock(HybridRetriever.class, invocation ->
                    invocation.getMethod().isDefault()
                            ? invocation.callRealMethod()
                            : java.util.Collections.emptyList());

            assertThat(retriever.retrieve("q", List.of("kb-1"), 5)).isEmpty();
            verify(retriever).retrieve(anyString(), any(RetrievalOptions.class));
        }
    }

    @Nested
    class InterfaceShape {

        @Test
        void shouldHaveExactlyOneAbstractionAndFiveDefaultEntries() {
            java.lang.reflect.Method[] methods = HybridRetriever.class.getDeclaredMethods();
            long defaults = Arrays.stream(methods).filter(java.lang.reflect.Method::isDefault).count();

            assertThat(HybridRetriever.class.isInterface()).isTrue();
            assertThat(methods).hasSize(6);
            assertThat(defaults).isEqualTo(5);
            assertThat(Arrays.stream(methods).filter(m -> !m.isDefault()).map(java.lang.reflect.Method::getName))
                    .containsExactly("retrieve");
        }
    }
}
