package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link AuditHook} 单元测试：前置判定恒放行，后置回调对拒绝、审批挂起、超时、
 * 截断输出等各类结果均需 null 安全、不抛异常。
 */
class AuditHookTest {

    /** 被测试审计钩子 */
    private AuditHook hook;

    @BeforeEach
    void setUp() {
        hook = new AuditHook();
    }

    /** 钩子顺序应为 900 */
    @Test
    void shouldExposeOrder900() {
        assertThat(hook.getOrder()).isEqualTo(900);
    }

    /** 前置判定恒为放行 */
    @Test
    void shouldAlwaysProceedBefore() {
        ToolInvocation invocation = ToolInvocation.builder().callName("any").build();

        assertThat(hook.before(invocation, null).isProceed()).isTrue();
    }

    /** 后置结果为 null 时直接返回，不抛异常 */
    @Test
    void shouldTolerateNullOutcome() {
        assertThatCode(() -> hook.after(ToolInvocation.builder().build(), null, null))
                .doesNotThrowAnyException();
    }

    /** 策略拒绝结果记录告警，不抛异常 */
    @Test
    void shouldHandleDeniedOutcome() {
        ToolInvocation invocation = ToolInvocation.builder().runId("r").callName("tool").build();
        ToolOutcome outcome = ToolOutcome.denied("blocked");

        assertThatCode(() -> hook.after(invocation, outcome, null)).doesNotThrowAnyException();
    }

    /** 审批挂起结果记录信息，不抛异常 */
    @Test
    void shouldHandleWaitingApprovalOutcome() {
        ToolInvocation invocation = ToolInvocation.builder().runId("r").callName("tool").build();
        ToolOutcome outcome = ToolOutcome.waitingApproval("reason", "high");

        assertThatCode(() -> hook.after(invocation, outcome, null)).doesNotThrowAnyException();
    }

    /** 超时结果记录告警，不抛异常 */
    @Test
    void shouldHandleTimeoutOutcome() {
        ToolInvocation invocation = ToolInvocation.builder().runId("r").callName("tool").build();
        ToolOutcome outcome = ToolOutcome.timeout("slow", 30000L);

        assertThatCode(() -> hook.after(invocation, outcome, null)).doesNotThrowAnyException();
        assertThat(outcome.getStatus()).isEqualTo(ToolStatus.TIMEOUT);
    }

    /** 截断输出结果记录信息，输出为 null 时也安全 */
    @Test
    void shouldHandleTruncatedOutcomeWithNullOutput() {
        ToolInvocation invocation = ToolInvocation.builder().runId("r").callName("tool").build();
        ToolOutcome outcome = ToolOutcome.builder()
                .status(ToolStatus.SUCCESS).truncated(true).output(null)
                .build();

        assertThatCode(() -> hook.after(invocation, outcome, null)).doesNotThrowAnyException();
    }

    /** 成功且未截断时不产生任何审计动作 */
    @Test
    void shouldIgnorePlainSuccess() {
        ToolInvocation invocation = ToolInvocation.builder().runId("r").callName("tool").build();
        ToolOutcome outcome = ToolOutcome.success("ok", 10L);

        assertThatCode(() -> hook.after(invocation, outcome, null)).doesNotThrowAnyException();
    }
}
