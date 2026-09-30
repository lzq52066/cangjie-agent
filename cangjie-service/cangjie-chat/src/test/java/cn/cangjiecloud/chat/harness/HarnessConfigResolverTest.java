package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.ModelSettings;
import cn.cangjiecloud.core.harness.context.ContextBudget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HarnessConfigResolver} 单元测试：覆盖全局默认与应用级覆盖合并的纯逻辑，
 * 包括配置解析容错、循环策略回落、温度回落、上下文预算与审批参数合并。
 */
class HarnessConfigResolverTest {

    /** 被测试解析器，使用可定制的真实全局属性对象 */
    private HarnessConfigResolver resolver;

    /** 全局默认属性 */
    private HarnessProperties properties;

    @BeforeEach
    void setUp() {
        properties = new HarnessProperties();
        resolver = new HarnessConfigResolver(properties);
    }

    /**
     * 构造带 config 的应用实体
     */
    private ApplicationEntity applicationWithConfig(String config) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId("1");
        entity.setConfig(config);
        return entity;
    }

    @Nested
    class Parse {

        /** 应用为空或 config 空白时返回 null，由调用方回落全局默认 */
        @Test
        void shouldReturnNullWhenApplicationNullOrConfigBlank() {
            assertThat(resolver.parse(null)).isNull();
            assertThat(resolver.parse(applicationWithConfig(""))).isNull();
            assertThat(resolver.parse(applicationWithConfig("   "))).isNull();
        }

        /** 根节点不含 harness 节点时返回 null */
        @Test
        void shouldReturnNullWhenHarnessNodeMissing() {
            HarnessConfig config = resolver.parse(applicationWithConfig("{\"other\":1}"));
            assertThat(config).isNull();
        }

        /** config 为非法 JSON 时容错返回 null */
        @Test
        void shouldReturnNullWhenConfigIsInvalidJson() {
            assertThat(resolver.parse(applicationWithConfig("not-a-json"))).isNull();
        }

        /** harness 节点存在时反序列化为配置对象 */
        @Test
        void shouldParseHarnessNode() {
            String json = "{\"harness\":{\"runTokenBudget\":1234,\"recordSteps\":false}}";
            HarnessConfig config = resolver.parse(applicationWithConfig(json));
            assertThat(config).isNotNull();
            assertThat(config.getRunTokenBudget()).isEqualTo(1234L);
            assertThat(config.getRecordSteps()).isFalse();
        }
    }

    @Nested
    class LoopPolicyMerge {

        /** 无应用配置时全部取全局默认，并行工具默认关闭 */
        @Test
        void shouldFallbackToGlobalDefaults() {
            LoopPolicy policy = resolver.loopPolicy(null);

            assertThat(policy.getMaxRounds()).isEqualTo(5);
            assertThat(policy.getTimeoutSeconds()).isEqualTo(300L);
            assertThat(policy.getToolTimeoutSeconds()).isEqualTo(30L);
            assertThat(policy.isParallelTools()).isFalse();
        }

        /** 应用显式覆盖的值优先生效 */
        @Test
        void shouldPreferApplicationOverrides() {
            properties.setMaxRounds(8);
            HarnessConfig config = HarnessConfig.builder()
                    .loop(HarnessConfig.Loop.builder()
                            .maxRounds(12)
                            .timeoutSeconds(600L)
                            .toolTimeoutSeconds(45L)
                            .parallelTools(Boolean.TRUE)
                            .build())
                    .build();

            LoopPolicy policy = resolver.loopPolicy(config);

            assertThat(policy.getMaxRounds()).isEqualTo(12);
            assertThat(policy.getTimeoutSeconds()).isEqualTo(600L);
            assertThat(policy.getToolTimeoutSeconds()).isEqualTo(45L);
            assertThat(policy.isParallelTools()).isTrue();
        }

        /** 应用值为 null 或非正数时回落到全局默认 */
        @Test
        void shouldFallbackWhenOverrideNullOrNonPositive() {
            properties.setMaxRounds(7);
            properties.setTimeoutSeconds(250L);
            properties.setToolTimeoutSeconds(20L);
            HarnessConfig config = HarnessConfig.builder()
                    .loop(HarnessConfig.Loop.builder()
                            .maxRounds(0)
                            .timeoutSeconds(-1L)
                            .toolTimeoutSeconds(null)
                            .parallelTools(Boolean.FALSE)
                            .build())
                    .build();

            LoopPolicy policy = resolver.loopPolicy(config);

            assertThat(policy.getMaxRounds()).isEqualTo(7);
            assertThat(policy.getTimeoutSeconds()).isEqualTo(250L);
            assertThat(policy.getToolTimeoutSeconds()).isEqualTo(20L);
            assertThat(policy.isParallelTools()).isFalse();
        }

        /** parallelTools 仅在显式为 TRUE 时开启 */
        @Test
        void shouldKeepParallelFalseWhenNull() {
            HarnessConfig config = HarnessConfig.builder()
                    .loop(HarnessConfig.Loop.builder().parallelTools(null).build())
                    .build();

            assertThat(resolver.loopPolicy(config).isParallelTools()).isFalse();
        }
    }

    @Nested
    class ModelSettingsMerge {

        /** 应用温度存在时使用应用值 */
        @Test
        void shouldUseApplicationTemperature() {
            ApplicationEntity entity = new ApplicationEntity();
            entity.setTemperature(0.35);

            ModelSettings settings = resolver.modelSettings(entity);

            assertThat(settings.getTemperature()).isEqualTo(0.35);
        }

        /** 应用为空或温度缺失时回落到 0.7 */
        @Test
        void shouldFbackTemperatureToZeroPointSeven() {
            assertThat(resolver.modelSettings(null).getTemperature()).isEqualTo(0.7);
            assertThat(resolver.modelSettings(new ApplicationEntity()).getTemperature()).isEqualTo(0.7);
        }
    }

    @Nested
    class Budget {

        /** 配置为空时使用全局属性预算（默认关闭） */
        @Test
        void shouldUseGlobalBudgetWhenConfigNull() {
            ContextBudget budget = resolver.budget(null);

            assertThat(budget.isEnabled()).isFalse();
        }

        /** 全局属性启用预算时，无应用上下文配置跟随全局启用 */
        @Test
        void shouldFollowEnabledGlobalBudget() {
            properties.getContext().setBudgetEnabled(true);
            properties.getContext().setBudgetTokens(5000);

            ContextBudget budget = resolver.budget(null);

            assertThat(budget.isEnabled()).isTrue();
            assertThat(budget.getBudgetTokens()).isEqualTo(5000);
        }

        /** 应用显式关闭预算时直接关闭，即使全局启用 */
        @Test
        void shouldBeDisabledWhenAppExplicitlyDisabled() {
            properties.getContext().setBudgetEnabled(true);
            properties.getContext().setBudgetTokens(5000);
            HarnessConfig config = HarnessConfig.builder()
                    .context(HarnessConfig.Context.builder().budgetEnabled(false).build())
                    .build();

            assertThat(resolver.budget(config).isEnabled()).isFalse();
        }

        /** 应用启用但 tokens 缺失或非正数时关闭 */
        @Test
        void shouldBeDisabledWhenTokensMissingOrNonPositive() {
            HarnessConfig noTokens = HarnessConfig.builder()
                    .context(HarnessConfig.Context.builder().budgetEnabled(true).build())
                    .build();
            HarnessConfig zeroTokens = HarnessConfig.builder()
                    .context(HarnessConfig.Context.builder().budgetEnabled(true).budgetTokens(0).build())
                    .build();

            assertThat(resolver.budget(noTokens).isEnabled()).isFalse();
            assertThat(resolver.budget(zeroTokens).isEnabled()).isFalse();
        }

        /** 应用显式启用且 tokens 为正时构建预算，缺省占比取全局 */
        @Test
        void shouldBuildBudgetWithGlobalRatiosWhenAppRatiosNull() {
            HarnessConfig config = HarnessConfig.builder()
                    .context(HarnessConfig.Context.builder()
                            .budgetEnabled(true).budgetTokens(8000).build())
                    .build();

            ContextBudget budget = resolver.budget(config);

            assertThat(budget.isEnabled()).isTrue();
            assertThat(budget.getBudgetTokens()).isEqualTo(8000);
            assertThat(budget.getSlotRatios()).isEqualTo(ContextBudget.DEFAULT_RATIOS);
        }

        /** 应用显式提供 slot 占比时合并到预算 */
        @Test
        void shouldUseAppSlotRatios() {
            Map<String, Double> ratios = Map.of("rag", 0.5, "history", 0.5);
            HarnessConfig config = HarnessConfig.builder()
                    .context(HarnessConfig.Context.builder()
                            .budgetEnabled(true).budgetTokens(6000).slotRatios(ratios).build())
                    .build();

            ContextBudget budget = resolver.budget(config);

            assertThat(budget.getSlotRatios().get("rag")).isEqualTo(0.5);
            assertThat(budget.getSlotRatios().get("history")).isEqualTo(0.5);
        }
    }

    /** 单次 run token 预算缺省为 0，显式配置时返回配置值 */
    @Test
    void shouldResolveRunTokenBudget() {
        assertThat(resolver.runTokenBudget(null)).isZero();

        HarnessConfig config = HarnessConfig.builder().runTokenBudget(999L).build();

        assertThat(resolver.runTokenBudget(config)).isEqualTo(999L);
    }

    @Nested
    class Approval {

        /** 无应用审批配置时回落到全局开关与超时 */
        @Test
        void shouldFallbackToGlobalApprovalSettings() {
            properties.setApprovalEnabled(true);
            properties.setApprovalTimeoutSeconds(600);

            assertThat(resolver.approvalEnabled(null)).isTrue();
            assertThat(resolver.approvalTimeoutSeconds(null)).isEqualTo(600);
        }

        /** 应用显式配置审批开关与超时时优先生效 */
        @Test
        void shouldPreferApplicationApprovalSettings() {
            HarnessConfig config = HarnessConfig.builder()
                    .approval(HarnessConfig.Approval.builder()
                            .enabled(true).timeoutSeconds(90).build())
                    .build();

            assertThat(resolver.approvalEnabled(config)).isTrue();
            assertThat(resolver.approvalTimeoutSeconds(config)).isEqualTo(90);
        }

        /** 应用显式关闭审批时返回 false */
        @Test
        void shouldReturnFalseWhenAppExplicitlyDisabled() {
            properties.setApprovalEnabled(true);
            HarnessConfig config = HarnessConfig.builder()
                    .approval(HarnessConfig.Approval.builder().enabled(false).build())
                    .build();

            assertThat(resolver.approvalEnabled(config)).isFalse();
        }
    }

    /** 工具输出字符上限取全局属性 */
    @Test
    void shouldExposeMaxToolOutputChars() {
        properties.setMaxToolOutputChars(4096);

        assertThat(resolver.maxToolOutputChars()).isEqualTo(4096);
    }

    /** step 记录：null 或 TRUE 均为 true，显式 FALSE 为 false */
    @Test
    void shouldResolveRecordSteps() {
        assertThat(resolver.recordSteps(null)).isTrue();
        assertThat(resolver.recordSteps(HarnessConfig.builder().recordSteps(null).build())).isTrue();
        assertThat(resolver.recordSteps(HarnessConfig.builder().recordSteps(Boolean.TRUE).build())).isTrue();
        assertThat(resolver.recordSteps(HarnessConfig.builder().recordSteps(Boolean.FALSE).build())).isFalse();
    }

    /** SSE 工具事件开关直接透传全局属性 */
    @Test
    void shouldExposeSseToolEvents() {
        assertThat(resolver.isSseToolEvents()).isFalse();
        properties.setSseToolEvents(true);
        assertThat(resolver.isSseToolEvents()).isTrue();
    }
}
