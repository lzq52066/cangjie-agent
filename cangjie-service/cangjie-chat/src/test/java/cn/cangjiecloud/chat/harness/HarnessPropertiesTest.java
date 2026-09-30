package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.context.ContextBudget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessProperties} 及其嵌套 {@link HarnessProperties.Context} 单元测试：
 * 默认值、setter 与 {@code toBudget()} 转换。
 */
class HarnessPropertiesTest {

    @Test
    void defaultsShouldMatchDocumentedGlobalDefaults() {
        HarnessProperties properties = new HarnessProperties();

        assertThat(properties.getMaxRounds()).isEqualTo(5);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(300);
        assertThat(properties.getToolTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getMaxToolOutputChars()).isEqualTo(8000);
        assertThat(properties.isApprovalEnabled()).isFalse();
        assertThat(properties.getApprovalTimeoutSeconds()).isEqualTo(1800);
        assertThat(properties.isSseToolEvents()).isFalse();
        assertThat(properties.getContext()).isNotNull();
    }

    @Test
    void settersShouldUpdateProperties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setMaxRounds(8);
        properties.setTimeoutSeconds(120);
        properties.setToolTimeoutSeconds(15);
        properties.setMaxToolOutputChars(4000);
        properties.setApprovalEnabled(true);
        properties.setApprovalTimeoutSeconds(600);
        properties.setSseToolEvents(true);

        assertThat(properties.getMaxRounds()).isEqualTo(8);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getToolTimeoutSeconds()).isEqualTo(15);
        assertThat(properties.getMaxToolOutputChars()).isEqualTo(4000);
        assertThat(properties.isApprovalEnabled()).isTrue();
        assertThat(properties.getApprovalTimeoutSeconds()).isEqualTo(600);
        assertThat(properties.isSseToolEvents()).isTrue();
    }

    @Nested
    class ContextToBudget {

        @Test
        void shouldReturnDisabledBudgetByDefault() {
            ContextBudget budget = new HarnessProperties.Context().toBudget();

            assertThat(budget.isEnabled()).isFalse();
            assertThat(budget.getBudgetTokens()).isZero();
        }

        @Test
        void shouldReturnEnabledBudgetWhenEnabledWithPositiveTokens() {
            HarnessProperties.Context context = new HarnessProperties.Context();
            context.setBudgetEnabled(true);
            context.setBudgetTokens(10_000);
            context.setSlotRatios(Map.of("rag", 0.5));

            ContextBudget budget = context.toBudget();

            assertThat(budget.isEnabled()).isTrue();
            assertThat(budget.getBudgetTokens()).isEqualTo(10_000);
            assertThat(budget.getSlotRatios()).containsEntry("rag", 0.5);
        }

        @Test
        void shouldReturnDisabledWhenEnabledButTokensZero() {
            HarnessProperties.Context context = new HarnessProperties.Context();
            context.setBudgetEnabled(true);
            context.setBudgetTokens(0);

            assertThat(context.toBudget().isEnabled()).isFalse();
        }

        @Test
        void contextDefaultsShouldUseDefaultRatios() {
            assertThat(new HarnessProperties.Context().getSlotRatios())
                    .isSameAs(ContextBudget.DEFAULT_RATIOS);
        }
    }
}
