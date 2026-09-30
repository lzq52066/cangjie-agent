package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolPolicyHook} 单元测试：覆盖无策略放行、黑名单拒绝（优先级最高）、
 * 白名单未命中拒绝，以及风险等级覆盖写回调用对象。
 */
class ToolPolicyHookTest {

    /** 被测试策略钩子 */
    private ToolPolicyHook hook;

    @BeforeEach
    void setUp() {
        hook = new ToolPolicyHook();
    }

    /**
     * 构造带指定策略的上下文
     */
    private HarnessContext contextWith(HarnessConfig.ToolPolicy policy) {
        HarnessConfig config = HarnessConfig.builder().toolPolicy(policy).build();
        return new HarnessContext(HarnessRequest.builder().config(config).build());
    }

    /**
     * 构造工具调用
     */
    private ToolInvocation invocation(String name) {
        return ToolInvocation.builder().callName(name).riskLevel("low").build();
    }

    /** 钩子顺序应为 200 */
    @Test
    void shouldExposeOrder200() {
        assertThat(hook.getOrder()).isEqualTo(200);
    }

    @Nested
    class Before {

        /** 无工具策略配置时直接放行 */
        @Test
        void shouldProceedWhenPolicyNull() {
            HarnessContext context = new HarnessContext(HarnessRequest.builder().build());

            assertThat(hook.before(invocation("anything"), context).isProceed()).isTrue();
        }

        /** 命中黑名单时拒绝，消息标明被策略禁用 */
        @Test
        void shouldDenyWhenNameInDenyList() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .deny(List.of("delete_*")).build();

            ToolCallHook.Decision decision = hook.before(
                    invocation("delete_file"), contextWith(policy));

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getReason()).contains("已被应用策略禁用");
        }

        /** 黑名单优先于白名单：同时命中时仍拒绝 */
        @Test
        void shouldGiveDenyPriorityOverAllow() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .allow(List.of("delete_file"))
                    .deny(List.of("delete_*"))
                    .build();

            assertThat(hook.before(invocation("delete_file"), contextWith(policy)).isDeny()).isTrue();
        }

        /** 白名单非空且未命中时拒绝，消息标明不在允许列表 */
        @Test
        void shouldDenyWhenNotInAllowList() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .allow(List.of("search", "query"))
                    .build();

            ToolCallHook.Decision decision = hook.before(invocation("create"), contextWith(policy));

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getReason()).contains("不在应用允许的工具列表内");
        }

        /** 白名单命中时放行 */
        @Test
        void shouldProceedWhenNameInAllowList() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .allow(List.of("search"))
                    .build();

            assertThat(hook.before(invocation("search"), contextWith(policy)).isProceed()).isTrue();
        }

        /** 白名单为 null/空时不限制 */
        @Test
        void shouldProceedWhenAllowListEmpty() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .allow(List.of())
                    .build();

            assertThat(hook.before(invocation("anything"), contextWith(policy)).isProceed()).isTrue();
        }

        /** 命中风险覆盖时把等级写回调用对象，值做 trim */
        @Test
        void shouldApplyRiskOverride() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .riskOverrides(Map.of("search", " high "))
                    .build();
            ToolInvocation invocation = invocation("search");

            hook.before(invocation, contextWith(policy));

            assertThat(invocation.getRiskLevel()).isEqualTo("high");
        }

        /** 首个匹配的风险覆盖生效，未命中时保持原等级 */
        @Test
        void shouldKeepRiskWhenNoOverrideMatches() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .riskOverrides(Map.of("delete_*", "high"))
                    .build();
            ToolInvocation invocation = invocation("search");

            hook.before(invocation, contextWith(policy));

            assertThat(invocation.getRiskLevel()).isEqualTo("low");
        }

        /** 风险覆盖值为空白时跳过，不写回 */
        @Test
        void shouldSkipBlankOverrideValue() {
            HarnessConfig.ToolPolicy policy = HarnessConfig.ToolPolicy.builder()
                    .riskOverrides(Map.of("search", "  "))
                    .build();
            ToolInvocation invocation = invocation("search");

            hook.before(invocation, contextWith(policy));

            assertThat(invocation.getRiskLevel()).isEqualTo("low");
        }
    }
}
