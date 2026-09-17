package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link HybridRetriever} 单元测试。
 * <p>
 * 接口仅有两个 default 重载（3 参兼容方法），重点验证它们是否以 0.0 阈值委托到 4 参抽象方法，
 * 且不吞掉入参、不改变返回引用。纯 JVM 测试，不加载 Spring、不连库。
 */
class HybridRetrieverTest {

    /** 记录被调用的重载签名与实参，并返回预设结果。 */
    private static final class RecordingRetriever implements HybridRetriever {

        private final List<String> invokedSignatures = new ArrayList<>();
        private Object[] lastArgs;
        private List<RetrievalResult> canned = new ArrayList<>();

        @Override
        public List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK,
                                              double similarityThreshold) {
            invokedSignatures.add("single-kb-threshold");
            lastArgs = new Object[]{query, knowledgeBaseId, topK, similarityThreshold};
            return canned;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK,
                                              double similarityThreshold) {
            invokedSignatures.add("multi-kb-threshold");
            lastArgs = new Object[]{query, knowledgeBaseIds, topK, similarityThreshold};
            return canned;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK,
                                              double similarityThreshold, String searchMode) {
            invokedSignatures.add("multi-kb-threshold-mode");
            lastArgs = new Object[]{query, knowledgeBaseIds, topK, similarityThreshold, searchMode};
            return canned;
        }
    }

    private static RetrievalResult result(String paragraphId, double score) {
        return RetrievalResult.builder().paragraphId(paragraphId).finalScore(score).build();
    }

    @Nested
    class DefaultOverloads {

        @Test
        void singleKnowledgeBaseShortcutShouldDelegateWithZeroThreshold() {
            RecordingRetriever retriever = new RecordingRetriever();
            List<RetrievalResult> expected = List.of(result("p1", 0.9));
            retriever.canned = expected;

            List<RetrievalResult> actual = retriever.retrieve("问题", "kb-1", 5);

            assertThat(retriever.invokedSignatures).containsExactly("single-kb-threshold");
            assertThat(retriever.lastArgs).containsExactly("问题", "kb-1", 5, 0.0);
            assertThat(actual).isSameAs(expected);
        }

        @Test
        void multiKnowledgeBaseShortcutShouldDelegateWithZeroThreshold() {
            RecordingRetriever retriever = new RecordingRetriever();
            List<String> ids = List.of("kb-1", "kb-2");

            retriever.retrieve("q", ids, 3);

            assertThat(retriever.invokedSignatures).containsExactly("multi-kb-threshold");
            assertThat(retriever.lastArgs).containsExactly("q", ids, 3, 0.0);
        }

        @Test
        void shortcutOverloadsShouldSelectDifferentAbstractions() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", 1);
            retriever.retrieve("q", List.of("kb"), 1);

            assertThat(retriever.invokedSignatures)
                    .containsExactly("single-kb-threshold", "multi-kb-threshold");
        }

        @Test
        void shortcutShouldPassThroughNullQueryAndNullKnowledgeBase() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve(null, (String) null, 0);

            assertThat(retriever.lastArgs).containsExactly(null, null, 0, 0.0);
        }

        @Test
        void shortcutShouldPassThroughNullKnowledgeBaseIdList() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", (List<String>) null, 2);

            assertThat(retriever.lastArgs).containsExactly("q", null, 2, 0.0);
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
            assertThat(retriever.lastArgs[2]).isEqualTo(-1);

            retriever.retrieve("q", List.of("kb"), Integer.MAX_VALUE);
            assertThat(retriever.lastArgs[2]).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @Nested
    class ExplicitOverloads {

        @Test
        void thresholdAwareOverloadsShouldNotRouteThroughDefaults() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", "kb", 4, 0.85);
            retriever.retrieve("q", List.of("kb"), 4, 0.75);
            retriever.retrieve("q", List.of("kb"), 4, 0.65, "two_stage");

            assertThat(retriever.invokedSignatures)
                    .containsExactly("single-kb-threshold", "multi-kb-threshold", "multi-kb-threshold-mode");
        }

        @Test
        void searchModeOverloadShouldReceiveModeArgumentVerbatim() {
            RecordingRetriever retriever = new RecordingRetriever();

            retriever.retrieve("q", List.of("kb"), 6, 0.5, "simple");
            assertThat(retriever.lastArgs).containsExactly("q", List.of("kb"), 6, 0.5, "simple");

            retriever.retrieve("q", List.of("kb"), 6, 0.5, null);
            assertThat(retriever.lastArgs[4]).isNull();
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
        void stubbedFourArgMethodShouldServeDefaultThreeArgCall() {
            HybridRetriever retriever = mock(HybridRetriever.class, invocation -> {
                if (invocation.getMethod().isDefault()) {
                    return invocation.callRealMethod();
                }
                return List.of(result("p1", 0.2));
            });

            assertThat(retriever.retrieve("q", "kb", 3)).hasSize(1);
            verify(retriever).retrieve("q", "kb", 3, 0.0);
        }

        @Test
        void unstubbedDefaultsOnRealMethodMockShouldDelegateToStubbedAbstraction() {
            HybridRetriever retriever = mock(HybridRetriever.class,
                    invocation -> {
                        if (invocation.getMethod().isDefault()) {
                            return invocation.callRealMethod();
                        }
                        return java.util.Collections.emptyList();
                    });

            assertThat(retriever.retrieve("q", List.of("kb-1"), 5)).isEmpty();
            verify(retriever).retrieve("q", List.of("kb-1"), 5, 0.0);
        }

        @Test
        void mockWithoutAnyCallShouldRecordNoInteractions() {
            HybridRetriever retriever = mock(HybridRetriever.class);

            verifyNoInteractions(retriever);
        }

        @Test
        void defaultMethodShouldWorkForListBasedSignatureOnMock() {
            HybridRetriever retriever = mock(HybridRetriever.class,
                    invocation -> invocation.getMethod().isDefault()
                            ? invocation.callRealMethod()
                            : null);

            assertThat(retriever.retrieve("q", List.of("kb"), 1)).isNull();
            verify(retriever).retrieve(anyString(), anyList(), anyInt(), anyDouble());
        }
    }

    @Nested
    class InterfaceShape {

        @Test
        void onlyThreeArgSignaturesShouldBeDefault() {
            long defaults = Arrays.stream(HybridRetriever.class.getDeclaredMethods())
                    .filter(java.lang.reflect.Method::isDefault)
                    .count();

            assertThat(HybridRetriever.class.isInterface()).isTrue();
            assertThat(defaults).isEqualTo(2);
            assertThat(HybridRetriever.class.getDeclaredMethods()).hasSize(5);
        }
    }
}
