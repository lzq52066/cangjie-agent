package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalHook} 单元测试：覆盖审批总开关、已放行去重、三类触发条件
 * （元信息标记、高风险、配置函数匹配）以及审批理由的拼装与截断。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalHookTest {

    /** 配置解析器桩：控制审批总开关 */
    @Mock
    private HarnessConfigResolver configResolver;

    /** 被测试审批钩子 */
    private ApprovalHook hook;

    @BeforeEach
    void setUp() {
        hook = new ApprovalHook(configResolver);
    }

    /**
     * 构造带指定配置的运行时上下文
     */
    private HarnessContext contextWith(HarnessConfig config) {
        return new HarnessContext(HarnessRequest.builder().config(config).build());
    }

    /**
     * 构造工具调用
     */
    private ToolInvocation invocation(String name, String risk, boolean requireApproval) {
        return ToolInvocation.builder()
                .runId("run-1").callId("call-1").callName(name)
                .riskLevel(risk).requireApproval(requireApproval)
                .argumentsJson("{}")
                .build();
    }

    @Test
    /** 钩子顺序应为 300 */
    void shouldExposeOrder300() {
        assertThat(hook.getOrder()).isEqualTo(300);
    }

    @Nested
    class Before {

        /** 审批总开关关闭时直接放行 */
        @Test
        void shouldProceedWhenApprovalDisabled() {
            when(configResolver.approvalEnabled(any())).thenReturn(false);

            ToolCallHook.Decision decision = hook.before(invocation("del", "high", true), contextWith(null));

            assertThat(decision.isProceed()).isTrue();
        }

        /** 已获人工放行的调用直接放行，避免二次挂起 */
        @Test
        void shouldProceedWhenAlreadyApproved() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);
            HarnessContext context = contextWith(null);
            context.approve("call-1");

            ToolCallHook.Decision decision = hook.before(invocation("del", "high", true), context);

            assertThat(decision.isProceed()).isTrue();
        }

        /** 工具元信息要求审批时挂起 */
        @Test
        void shouldRequireApprovalWhenInvocationMarked() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);

            ToolCallHook.Decision decision = hook.before(
                    invocation("normal_tool", "low", true), contextWith(null));

            assertThat(decision.isRequireApproval()).isTrue();
        }

        /** 风险等级为 high（忽略大小写）时挂起 */
        @Test
        void shouldRequireApprovalForHighRisk() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);

            ToolCallHook.Decision decision = hook.before(
                    invocation("delete_user", "HIGH", false), contextWith(null));

            assertThat(decision.isRequireApproval()).isTrue();
            assertThat(decision.getRiskLevel()).isEqualTo("HIGH");
        }

        /** 命中应用配置的 requireFunctions 时挂起 */
        @Test
        void shouldRequireApprovalForConfiguredFunctions() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);
            HarnessConfig config = HarnessConfig.builder()
                    .approval(HarnessConfig.Approval.builder()
                            .requireFunctions(List.of("delete_*")).build())
                    .build();

            ToolCallHook.Decision decision = hook.before(
                    invocation("delete_file", "low", false), contextWith(config));

            assertThat(decision.isRequireApproval()).isTrue();
        }

        /** 无任何触发条件时放行 */
        @Test
        void shouldProceedWhenNoTrigger() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);
            HarnessConfig config = HarnessConfig.builder()
                    .approval(HarnessConfig.Approval.builder()
                            .requireFunctions(List.of("delete_*")).build())
                    .build();

            ToolCallHook.Decision decision = hook.before(
                    invocation("search", "low", false), contextWith(config));

            assertThat(decision.isProceed()).isTrue();
        }

        /** 风险等级为 null 时理由中回落展示 medium */
        @Test
        void shouldUseMediumInReasonWhenRiskNull() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);

            ToolCallHook.Decision decision = hook.before(
                    invocation("danger", null, true), contextWith(null));

            assertThat(decision.getReason()).contains("风险等级：medium");
        }

        /** 入参 JSON 超过 500 字符时截断并追加省略号 */
        @Test
        void shouldTruncateLongArgumentsInReason() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);
            String longArgs = "x".repeat(600);
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("danger").riskLevel("high").requireApproval(false)
                    .argumentsJson(longArgs).build();

            ToolCallHook.Decision decision = hook.before(invocation, contextWith(null));

            assertThat(decision.getReason()).contains("...");
            assertThat(decision.getReason()).doesNotContain(longArgs);
        }

        /** 入参 JSON 为 null 时理由中不拼接入参段 */
        @Test
        void shouldOmitArgumentsSectionWhenNull() {
            when(configResolver.approvalEnabled(any())).thenReturn(true);
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("danger").riskLevel("high")
                    .argumentsJson(null).build();

            ToolCallHook.Decision decision = hook.before(invocation, contextWith(null));

            assertThat(decision.getReason()).doesNotContain("入参");
        }
    }
}
