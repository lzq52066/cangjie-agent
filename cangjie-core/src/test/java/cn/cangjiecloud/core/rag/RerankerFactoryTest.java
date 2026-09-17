package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RerankerFactory} 单元测试。
 */
class RerankerFactoryTest {

    private Reranker reranker(String type) {
        Reranker reranker = mock(Reranker.class);
        when(reranker.getType()).thenReturn(type);
        return reranker;
    }

    @Test
    void constructorShouldIndexRerankersByType() {
        Reranker none = reranker("none");
        Reranker bge = reranker("bge");
        Reranker cohere = reranker("cohere");

        RerankerFactory factory = new RerankerFactory(List.of(none, bge, cohere));

        assertThat(factory.get("none")).isSameAs(none);
        assertThat(factory.get("bge")).isSameAs(bge);
        assertThat(factory.get("cohere")).isSameAs(cohere);
    }

    @Test
    void nullTypeShouldFallBackToDefaultNone() {
        Reranker none = reranker("none");
        RerankerFactory factory = new RerankerFactory(List.of(none, reranker("bge")));

        assertThat(factory.get(null)).isSameAs(none);
    }

    @Test
    void unknownTypeShouldFallBackToDefaultNone() {
        Reranker none = reranker("none");
        RerankerFactory factory = new RerankerFactory(List.of(reranker("bge"), none));

        assertThat(factory.get("unknown")).isSameAs(none);
    }

    @Test
    void unknownTypeShouldReturnNullWhenDefaultMissing() {
        Reranker bge = reranker("bge");
        RerankerFactory factory = new RerankerFactory(List.of(bge));

        assertThat(factory.get("nope")).isNull();
        assertThat(factory.get(null)).isNull();
        assertThat(factory.get("bge")).isSameAs(bge);
    }

    @Test
    void emptyFactoryShouldReturnNullForAnyType() {
        RerankerFactory factory = new RerankerFactory(List.of());

        assertThat(factory.get("none")).isNull();
        assertThat(factory.get("bge")).isNull();
        assertThat(factory.get(null)).isNull();
    }

    @Test
    void firstRegisteredRerankerShouldWinForDuplicateType() {
        Reranker first = reranker("bge");
        Reranker second = reranker("bge");

        RerankerFactory factory = new RerankerFactory(List.of(first, second));

        assertThat(factory.get("bge")).isSameAs(first);
    }

    @Test
    void retrievedRerankerShouldStillDelegateRerank() {
        Reranker none = reranker("none");
        RetrievalResult result = RetrievalResult.builder().paragraphId("p1").build();
        when(none.rerank("q", List.of(result), 1)).thenReturn(List.of(result));

        RerankerFactory factory = new RerankerFactory(List.of(none));

        assertThat(factory.get("none").rerank("q", List.of(result), 1)).containsExactly(result);
    }

    @Test
    void nullRerankerListShouldFailFastInConstructor() {
        assertThatThrownBy(() -> new RerankerFactory(null)).isInstanceOf(NullPointerException.class);
    }
}
