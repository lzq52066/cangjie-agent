package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link QueryRewriterFactory} 单元测试：命中、null 键兜底、默认 NoOp 缺失、重复类型保留首个。
 */
class QueryRewriterFactoryTest {

    private QueryRewriter rewriter(String type) {
        QueryRewriter rewriter = mock(QueryRewriter.class);
        when(rewriter.getType()).thenReturn(type);
        return rewriter;
    }

    @Test
    void constructorShouldIndexRewritersByType() {
        QueryRewriter none = rewriter("none");
        QueryRewriter llm = rewriter("llm");

        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(none, llm));

        assertThat(factory.get("llm")).isSameAs(llm);
        assertThat(factory.get("none")).isSameAs(none);
    }

    @Test
    void nullTypeShouldFallBackToNoneRewriter() {
        QueryRewriter none = rewriter("none");
        QueryRewriter llm = rewriter("llm");
        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(none, llm));

        assertThat(factory.get(null)).isSameAs(none);
    }

    @Test
    void unknownTypeShouldFallBackToNoneRewriter() {
        QueryRewriter none = rewriter("none");
        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(none, rewriter("llm")));

        assertThat(factory.get("does-not-exist")).isSameAs(none);
        assertThat(factory.get("")).isSameAs(none);
    }

    @Test
    void unknownTypeShouldYieldNullWhenNoDefaultRegistered() {
        QueryRewriter llm = rewriter("llm");
        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(llm));

        assertThat(factory.get("missing")).isNull();
        assertThat(factory.get(null)).isNull();
        assertThat(factory.get("llm")).isSameAs(llm);
    }

    @Test
    void emptyFactoryShouldAlwaysReturnNull() {
        QueryRewriterFactory factory = new QueryRewriterFactory(List.of());

        assertThat(factory.get("none")).isNull();
        assertThat(factory.get(null)).isNull();
    }

    @Test
    void firstRegisteredRewriterShouldWinForDuplicateType() {
        QueryRewriter first = rewriter("llm");
        QueryRewriter second = rewriter("llm");

        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(first, second));

        assertThat(factory.get("llm")).isSameAs(first);
    }

    @Test
    void nullTypeKeyShouldNotBreakRegistrationButIsUnreachableByLookup() {
        QueryRewriter nullTyped = rewriter(null);
        QueryRewriter none = rewriter("none");

        assertThatCode(() -> new QueryRewriterFactory(Arrays.asList(nullTyped, none)))
                .doesNotThrowAnyException();

        QueryRewriterFactory factory = new QueryRewriterFactory(Arrays.asList(nullTyped, none));
        // 查找 key 为 null 时会被规范化成 "none"，因此 nullTyped 永远取不到
        assertThat(factory.get(null)).isSameAs(none);
    }

    @Test
    void retrievedRewriterShouldStillDelegateRewrite() {
        QueryRewriter llm = rewriter("llm");
        when(llm.rewrite("q", List.of())).thenReturn("rewritten");

        QueryRewriterFactory factory = new QueryRewriterFactory(List.of(llm));

        assertThat(factory.get("llm").rewrite("q", List.of())).isEqualTo("rewritten");
    }

    @Test
    void nullRewriterListShouldFailFastInConstructor() {
        assertThatThrownBy(() -> new QueryRewriterFactory(null)).isInstanceOf(NullPointerException.class);
    }
}
