package cn.cangjiecloud.core.harness.context;

import cn.cangjiecloud.core.model.ChatMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextFragment} 单元测试。
 */
class ContextFragmentTest {

    @Test
    void ofShouldInheritCompressibilityFromSlot() {
        ContextFragment rag = ContextFragment.of(ContextSlot.RAG, "rag",
                List.of(ChatMessage.user("x")));
        ContextFragment user = ContextFragment.of(ContextSlot.USER, "user",
                List.of(ChatMessage.user("x")));

        assertThat(rag.isCompressible()).isTrue();
        assertThat(user.isCompressible()).isFalse();
        assertThat(rag.getSlot()).isEqualTo(ContextSlot.RAG);
        assertThat(rag.getSource()).isEqualTo("rag");
    }

    @Test
    void emptyShouldProduceFragmentWithoutMessages() {
        ContextFragment empty = ContextFragment.empty(ContextSlot.MEMORY, "memory");

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.size()).isZero();
        assertThat(empty.getEstTokens()).isZero();
        assertThat(empty.isCompressible()).isTrue();
    }

    @Test
    void isEmptyAndSizeShouldHandleNullAndPopulatedMessages() {
        ContextFragment nullMessages = ContextFragment.builder().messages(null).build();
        ContextFragment populated = ContextFragment.of(ContextSlot.HISTORY, "h",
                List.of(ChatMessage.user("a"), ChatMessage.user("b")));

        assertThat(nullMessages.isEmpty()).isTrue();
        assertThat(nullMessages.size()).isZero();
        assertThat(populated.isEmpty()).isFalse();
        assertThat(populated.size()).isEqualTo(2);
    }

    @Nested
    class RefreshEstTokens {

        @Test
        void shouldCountTokensOfMessages() {
            ContextFragment fragment = ContextFragment.of(ContextSlot.HISTORY, "h",
                    List.of(ChatMessage.user("hello world")));

            fragment.refreshEstTokens();

            assertThat(fragment.getEstTokens()).isPositive();
        }

        @Test
        void shouldBeZeroForEmptyMessages() {
            ContextFragment fragment = ContextFragment.empty(ContextSlot.HISTORY, "h");

            fragment.refreshEstTokens();

            assertThat(fragment.getEstTokens()).isZero();
        }
    }
}
