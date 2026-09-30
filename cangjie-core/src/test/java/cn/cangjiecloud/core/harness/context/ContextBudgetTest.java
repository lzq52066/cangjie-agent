package cn.cangjiecloud.core.harness.context;

import cn.cangjiecloud.core.model.ChatMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextBudget} 单元测试，重点覆盖 {@link ContextBudget#apply(List)} 的裁剪算法。
 */
class ContextBudgetTest {

    private static ChatMessage user(String text) {
        return ChatMessage.user(text);
    }

    private static List<ChatMessage> historyMessages(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            messages.add(user("history message number " + i + " with some padding text"));
        }
        return messages;
    }

    // ---------- 构建 ----------

    @Test
    void disabledShouldReturnInactiveBudget() {
        ContextBudget budget = ContextBudget.disabled();

        assertThat(budget.isEnabled()).isFalse();
        assertThat(budget.getBudgetTokens()).isZero();
    }

    @Test
    void ofShouldEnableWhenBudgetPositive() {
        ContextBudget budget = ContextBudget.of(1000, null);

        assertThat(budget.isEnabled()).isTrue();
        assertThat(budget.getBudgetTokens()).isEqualTo(1000);
    }

    @Test
    void ofShouldStayDisabledWhenBudgetNonPositive() {
        assertThat(ContextBudget.of(0, null).isEnabled()).isFalse();
        assertThat(ContextBudget.of(-5, null).isEnabled()).isFalse();
    }

    @Test
    void ofShouldMergeCustomRatiosOverDefaultsAndIgnoreInvalidEntries() {
        ContextBudget budget = ContextBudget.of(1000, Map.of(
                "history", 0.50,
                "not-a-slot", 0.99,
                "rag", -1d,
                "memory", 0d));

        assertThat(budget.ratioOf(ContextSlot.HISTORY)).isEqualTo(0.50);
        // 非法槽位 / 非正占比不影响默认值
        assertThat(budget.ratioOf(ContextSlot.MEMORY)).isEqualTo(0.15);
        // 未覆盖的默认占比保留
        assertThat(budget.ratioOf(ContextSlot.RAG)).isEqualTo(0.30);
    }

    @Test
    void ratioOfShouldReturnZeroWhenRatiosUnset() {
        ContextBudget budget = ContextBudget.builder().slotRatios(null).build();

        assertThat(budget.ratioOf(ContextSlot.HISTORY)).isZero();
    }

    // ---------- apply ----------

    @Nested
    class Apply {

        @Test
        void shouldReturnFragmentsUnchangedWhenDisabled() {
            ContextBudget budget = ContextBudget.disabled();
            List<ContextFragment> fragments = new ArrayList<>(List.of(
                    ContextFragment.of(ContextSlot.HISTORY, "history", historyMessages(3))));

            List<ContextFragment> result = budget.apply(fragments);

            assertThat(result).isSameAs(fragments);
            assertThat(result.get(0).size()).isEqualTo(3);
        }

        @Test
        void shouldReturnUnchangedForNullOrEmptyInput() {
            ContextBudget budget = ContextBudget.of(1000, null);

            assertThat(budget.apply(null)).isNull();
            assertThat(budget.apply(new ArrayList<>())).isEmpty();
        }

        @Test
        void shouldKeepAllMessagesWhenWithinBudget() {
            ContextBudget budget = ContextBudget.of(1_000_000, null);
            ContextFragment history = ContextFragment.of(ContextSlot.HISTORY, "history", historyMessages(4));
            List<ContextFragment> fragments = new ArrayList<>(List.of(history));

            budget.apply(fragments);

            assertThat(history.size()).isEqualTo(4);
            assertThat(history.getDroppedRatio()).isZero();
            assertThat(history.getDroppedTokens()).isZero();
        }

        @Test
        void shouldDropOldHistoryMessagesButKeepNewest() {
            // 预算 70：USER(7) 先扣除，剩余 63 全给历史（仅一个可压缩片段时占比归一化为 1）。
            // 8 条历史共 72 token，从头丢弃最旧 1 条到 63 为止，保留 7 条
            ContextBudget budget = ContextBudget.of(70, null);
            ContextFragment user = ContextFragment.of(ContextSlot.USER, "user",
                    List.of(user("the current question asked by the user")));
            ContextFragment history = ContextFragment.of(ContextSlot.HISTORY, "history", historyMessages(8));
            List<ContextFragment> fragments = new ArrayList<>(List.of(user, history));

            budget.apply(fragments);

            // 本轮输入完整保留
            assertThat(user.size()).isEqualTo(1);
            // 历史被裁剪
            assertThat(history.size()).isEqualTo(7);
            // 最新两条始终保留
            ChatMessage newest = history.getMessages().get(history.size() - 1);
            assertThat(newest.getContent()).isEqualTo("history message number 8 with some padding text");
            assertThat(history.getDroppedTokens()).isPositive();
            assertThat(history.getDroppedRatio()).isGreaterThan(0d).isLessThanOrEqualTo(1d);
        }

        @Test
        void shouldAlwaysKeepNonCompressibleFragmentEvenWhenFixedExceedsBudget() {
            // 预算极小：固定部分（USER）已超预算，remaining=0，历史压缩到最后一条
            ContextBudget budget = ContextBudget.of(1, null);
            ContextFragment user = ContextFragment.of(ContextSlot.USER, "user",
                    List.of(user("the current question asked by the user")));
            ContextFragment history = ContextFragment.of(ContextSlot.HISTORY, "history", historyMessages(6));
            List<ContextFragment> fragments = new ArrayList<>(List.of(user, history));

            budget.apply(fragments);

            assertThat(user.size()).isEqualTo(1);
            assertThat(history.size()).isEqualTo(1);
            assertThat(history.getMessages().get(0).getContent())
                    .isEqualTo("history message number 6 with some padding text");
        }
    }
}
