package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.LoopPolicy;
import cn.cangjiecloud.core.harness.SpecialToolRoute;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.harness.ToolOutcome;
import cn.cangjiecloud.core.harness.ToolStatus;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolGatewayImpl} 单元测试：覆盖生命周期初始化、特殊路由、元信息预解析、
 * 钩子链拦截、本地工具挂起、超时执行与输出截断/失败识别。
 */
@ExtendWith(MockitoExtension.class)
class ToolGatewayImplTest {

    /** 工具服务桩 */
    @Mock
    private IToolService toolService;

    /** 配置解析器桩 */
    @Mock
    private HarnessConfigResolver configResolver;

    /** 钩子桩 */
    @Mock
    private ToolCallHook hook;

    /** 特殊路由桩 */
    @Mock
    private SpecialToolRoute route;

    /** 被测试工具网关 */
    private ToolGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        when(route.name()).thenReturn("special");
        gateway = new ToolGatewayImpl(toolService, configResolver,
                List.of(hook), List.of(route));
        gateway.init();
    }

    @AfterEach
    void tearDown() {
        gateway.destroy();
    }

    /**
     * 构造工具调用
     */
    private ToolInvocation invocation(String name) {
        return ToolInvocation.builder().runId("r1").callId("c1").callName(name).build();
    }

    /**
     * 构造带循环策略的上下文
     */
    private HarnessContext contextWith(LoopPolicy policy) {
        return new HarnessContext(HarnessRequest.builder().loopPolicy(policy).build());
    }

    /** resolveSpecs 直接透传工具服务 */
    @Test
    void shouldResolveSpecsViaToolService() {
        List<ToolSpecification> specs = List.of(ToolSpecification.builder().name("t").build());
        when(toolService.getToolSpecifications(anyList())).thenReturn(specs);

        assertThat(gateway.resolveSpecs(List.of("t1"))).isSameAs(specs);
    }

    @Nested
    class LocalTool {

        /** 元信息解析为 LOCAL 类型时直接挂起，不走钩子、不执行 */
        @Test
        void shouldWaitLocalForLocalToolType() {
            ToolEntity entity = new ToolEntity();
            entity.setId("t1");
            entity.setType("CUSTOM");
            entity.setToolType("LOCAL");
            when(toolService.resolveByCallName("browser_fs")).thenReturn(entity);

            ToolOutcome outcome = gateway.invoke(
                    invocation("browser_fs"), contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.WAITING_LOCAL);
        }
    }

    @Nested
    class Hooks {

        /** 钩子返回拒绝时，结果为 DENIED 且不执行工具 */
        @Test
        void shouldDenyWhenHookDenies() {
            when(toolService.resolveByCallName("tool_a")).thenReturn(null);
            when(hook.before(any(), any()))
                    .thenReturn(ToolCallHook.Decision.deny("被拦截"));

            ToolOutcome outcome = gateway.invoke(
                    invocation("tool_a"), contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.DENIED);
            assertThat(outcome.getError()).isEqualTo("被拦截");
        }

        /** 钩子要求审批时结果为 WAITING_APPROVAL */
        @Test
        void shouldWaitApprovalWhenHookRequires() {
            when(toolService.resolveByCallName("tool_a")).thenReturn(null);
            when(hook.before(any(), any()))
                    .thenReturn(ToolCallHook.Decision.requireApproval("危险", "high"));

            ToolOutcome outcome = gateway.invoke(
                    invocation("tool_a"), contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.WAITING_APPROVAL);
            assertThat(outcome.getRiskLevel()).isEqualTo("high");
        }

        /** 钩子抛异常时按放行处理，继续执行 */
        @Test
        void shouldProceedWhenHookThrows() {
            when(toolService.resolveByCallName("tool_a")).thenReturn(null);
            when(hook.before(any(), any())).thenThrow(new RuntimeException("hook error"));
            when(toolService.executeToolCall(anyString(), any())).thenReturn("结果");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            ToolOutcome outcome = gateway.invoke(
                    invocation("tool_a"), contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.SUCCESS);
        }

        /** 钩子返回 null 判定时按放行处理 */
        @Test
        void shouldProceedWhenDecisionNull() {
            when(toolService.resolveByCallName("tool_a")).thenReturn(null);
            when(hook.before(any(), any())).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any())).thenReturn("结果");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            assertThat(gateway.invoke(invocation("tool_a"),
                    contextWith(LoopPolicy.builder().build())).isSuccess()).isTrue();
        }

        /** 后置钩子无论是否抛异常都不影响返回结果 */
        @Test
        void shouldIgnoreAfterHookFailure() {
            when(toolService.resolveByCallName("tool_a")).thenReturn(null);
            org.mockito.Mockito.doThrow(new RuntimeException("after error"))
                    .when(hook).after(any(), any(), any());
            when(toolService.executeToolCall(anyString(), any())).thenReturn("结果");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            assertThat(gateway.invoke(invocation("tool_a"),
                    contextWith(LoopPolicy.builder().build())).isSuccess()).isTrue();
        }
    }

    @Nested
    class Routes {

        /** 命中特殊路由时执行路由而非工具服务，且不再解析元信息 */
        @Test
        void shouldExecuteMatchedRoute() {
            when(route.matches(any(), any())).thenReturn(true);
            when(hook.before(any(), any())).thenReturn(ToolCallHook.Decision.proceed());
            when(route.execute(any(), any())).thenReturn("路由结果");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            ToolOutcome outcome = gateway.invoke(
                    invocation("fancy"), contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getOutput()).isEqualTo("路由结果");
            verify(route).execute(any(), any());
        }

        /** 路由匹配抛异常时视为未命中，回落常规执行 */
        @Test
        void shouldTolerateRouteMatchFailure() {
            when(route.matches(any(), any())).thenThrow(new RuntimeException("match fail"));
            when(toolService.resolveByCallName("fancy")).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any())).thenReturn("直接结果");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            assertThat(gateway.invoke(invocation("fancy"),
                    contextWith(LoopPolicy.builder().build())).getOutput()).isEqualTo("直接结果");
        }
    }

    @Nested
    class Metadata {

        /** 技能前缀的函数名跳过元信息解析 */
        @Test
        void shouldSkipMetadataForSkillPrefix() {
            when(toolService.executeToolCall(anyString(), any())).thenReturn("ok");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            gateway.invoke(invocation("skill_mine"),
                    contextWith(LoopPolicy.builder().build()));

            verify(toolService, atLeastOnce()).executeToolCall(anyString(), any());
        }

        /** 元信息解析成功时写回工具 ID、类型、风险、审批标记与超时配置 */
        @Test
        void shouldPopulateInvocationFromToolEntity() {
            ToolEntity entity = new ToolEntity();
            entity.setId("tool-9");
            entity.setType("HTTP");
            entity.setToolType(null);
            entity.setRiskLevel("high");
            entity.setRequireApproval(1);
            entity.setTimeoutSeconds(15);
            entity.setMaxOutputChars(100);
            when(toolService.resolveByCallName("api_call")).thenReturn(entity);
            when(hook.before(any(), any())).thenReturn(ToolCallHook.Decision.proceed());
            when(toolService.executeToolCall(anyString(), any())).thenReturn("响应");

            ToolInvocation invocation = invocation("api_call");
            gateway.invoke(invocation, contextWith(LoopPolicy.builder().build()));

            assertThat(invocation.getToolId()).isEqualTo("tool-9");
            assertThat(invocation.getToolType()).isEqualTo("HTTP");
            assertThat(invocation.getRiskLevel()).isEqualTo("high");
            assertThat(invocation.isRequireApproval()).isTrue();
            assertThat(invocation.getTimeoutSeconds()).isEqualTo(15);
        }

        /** 元信息解析抛异常时按默认策略继续执行 */
        @Test
        void shouldTolerateMetadataFailure() {
            when(toolService.resolveByCallName("api_call"))
                    .thenThrow(new RuntimeException("db error"));
            when(toolService.executeToolCall(anyString(), any())).thenReturn("响应");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            assertThat(gateway.invoke(invocation("api_call"),
                    contextWith(LoopPolicy.builder().build())).isSuccess()).isTrue();
        }
    }

    @Nested
    class ExecutionResult {

        /** 工具返回 success:false 的 JSON 时归类为失败并提取 error */
        @Test
        void shouldClassifySuccessFalseJsonAsFailed() {
            when(toolService.resolveByCallName("t")).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any()))
                    .thenReturn("{\"success\":false,\"error\":\"参数错误\"}");
            when(configResolver.maxToolOutputChars()).thenReturn(8000);

            ToolOutcome outcome = gateway.invoke(invocation("t"),
                    contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.FAILED);
            assertThat(outcome.getError()).isEqualTo("参数错误");
        }

        /** 输出超过调用级上限时截断并标记 truncated，追加原始长度说明 */
        @Test
        void shouldTruncateByInvocationLimit() {
            when(toolService.resolveByCallName("t")).thenReturn(null);
            String longOutput = "x".repeat(50);
            when(toolService.executeToolCall(anyString(), any())).thenReturn(longOutput);

            ToolInvocation invocation = invocation("t");
            invocation.setMaxOutputChars(10);
            ToolOutcome outcome = gateway.invoke(invocation,
                    contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.isTruncated()).isTrue();
            assertThat(outcome.getOutput()).contains("工具输出已截断").contains("50");
        }

        /** 调用级上限缺失时使用全局限额 */
        @Test
        void shouldTruncateByGlobalLimit() {
            when(toolService.resolveByCallName("t")).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any()))
                    .thenReturn("y".repeat(30));
            when(configResolver.maxToolOutputChars()).thenReturn(20);

            ToolOutcome outcome = gateway.invoke(invocation("t"),
                    contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.isTruncated()).isTrue();
        }
    }

    @Nested
    class Timeout {

        /** 工具调用超过超时时间时返回 TIMEOUT，超时时间取自循环策略 */
        @Test
        void shouldTimeoutWhenExecutionExceedsLimit() {
            when(toolService.resolveByCallName("slow")).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any()))
                    .thenAnswer(inv -> {
                        Thread.sleep(2000);
                        return "done";
                    });
            LoopPolicy policy = LoopPolicy.builder().toolTimeoutSeconds(1L).build();

            ToolOutcome outcome = gateway.invoke(invocation("slow"), contextWith(policy));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.TIMEOUT);
            assertThat(outcome.getError()).contains("限时");
        }

        /** 工具内部直接抛异常时返回 FAILED 并提取根因消息 */
        @Test
        void shouldFailWithRootMessageWhenToolThrows() {
            when(toolService.resolveByCallName("boom")).thenReturn(null);
            when(toolService.executeToolCall(anyString(), any()))
                    .thenThrow(new RuntimeException(new IllegalStateException("根因")));

            ToolOutcome outcome = gateway.invoke(invocation("boom"),
                    contextWith(LoopPolicy.builder().build()));

            assertThat(outcome.getStatus()).isEqualTo(ToolStatus.FAILED);
            assertThat(outcome.getError()).isEqualTo("根因");
        }
    }
}
