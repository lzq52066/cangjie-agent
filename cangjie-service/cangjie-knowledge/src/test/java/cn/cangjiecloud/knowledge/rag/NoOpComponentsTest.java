package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.rag.RetrievalResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoOpQueryRewriter} 与 {@link NoOpReranker} 单元测试。
 * <p>
 * 覆盖：无操作实现的原样透传语义与 topK 截断行为。
 */
class NoOpComponentsTest {

    @Nested
    class 无操作查询改写器 {

        private final NoOpQueryRewriter rewriter = new NoOpQueryRewriter();

        @Test
        void getType应为none() {
            assertThat(rewriter.getType()).isEqualTo("none");
        }

        @Test
        void rewrite应原样返回query() {
            assertThat(rewriter.rewrite("原始查询", List.of(ChatMessage.user("你好")))).isEqualTo("原始查询");
            assertThat(rewriter.rewrite(null, null)).isNull();
        }
    }

    @Nested
    class 无操作重排序器 {

        private final NoOpReranker reranker = new NoOpReranker();

        @Test
        void getType应为none() {
            assertThat(reranker.getType()).isEqualTo("none");
        }

        @Test
        void 空候选应返回空列表() {
            assertThat(reranker.rerank("查询", null, 3)).isEmpty();
            assertThat(reranker.rerank("查询", List.of(), 3)).isEmpty();
        }

        @Test
        void 应按topK截断并保持原顺序() {
            RetrievalResult first = RetrievalResult.builder().paragraphId("p1").content("内容一").build();
            RetrievalResult second = RetrievalResult.builder().paragraphId("p2").content("内容二").build();
            RetrievalResult third = RetrievalResult.builder().paragraphId("p3").content("内容三").build();
            List<RetrievalResult> candidates = List.of(first, second, third);

            assertThat(reranker.rerank("查询", candidates, 2)).containsExactly(first, second);
            assertThat(reranker.rerank("查询", candidates, 10)).containsExactly(first, second, third);
        }
    }
}
