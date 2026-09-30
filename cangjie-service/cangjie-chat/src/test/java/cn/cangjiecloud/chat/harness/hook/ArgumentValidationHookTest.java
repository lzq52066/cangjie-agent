package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ArgumentValidationHook} 单元测试：覆盖函数名缺失、工具规格缺失、
 * 必填参数缺失/空白、参数齐全放行等分支。
 */
class ArgumentValidationHookTest {

    /** 被测试参数校验钩子 */
    private ArgumentValidationHook hook;

    @BeforeEach
    void setUp() {
        hook = new ArgumentValidationHook();
    }

    /**
     * 构造带必填参数规格的工具定义
     */
    private ToolSpecification specWithRequired(String name, List<String> required) {
        return ToolSpecification.builder()
                .name(name)
                .parameters(Map.of("required", required))
                .build();
    }

    /**
     * 用给定工具规格列表构造上下文
     */
    private HarnessContext contextWithTools(List<ToolSpecification> tools) {
        return new HarnessContext(HarnessRequest.builder().tools(tools).build());
    }

    /** 钩子顺序应为 100 */
    @Test
    void shouldExposeOrder100() {
        assertThat(hook.getOrder()).isEqualTo(100);
    }

    @Nested
    class Before {

        /** 函数名为空白时直接拒绝 */
        @Test
        void shouldDenyWhenCallNameBlank() {
            ToolInvocation invocation = ToolInvocation.builder().callName(" ").build();

            ToolCallHook.Decision decision = hook.before(invocation, contextWithTools(null));

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getReason()).contains("缺少函数名");
        }

        /** 上下文中找不到同名工具规格时放行 */
        @Test
        void shouldProceedWhenSpecNotFound() {
            ToolInvocation invocation = ToolInvocation.builder().callName("unknown").build();

            ToolCallHook.Decision decision = hook.before(invocation,
                    contextWithTools(List.of(specWithRequired("search", List.of("q")))));

            assertThat(decision.isProceed()).isTrue();
        }

        /** 工具规格没有参数定义时放行 */
        @Test
        void shouldProceedWhenParametersNull() {
            ToolSpecification spec = ToolSpecification.builder().name("search").build();
            ToolInvocation invocation = ToolInvocation.builder().callName("search").build();

            ToolCallHook.Decision decision = hook.before(invocation, contextWithTools(List.of(spec)));

            assertThat(decision.isProceed()).isTrue();
        }

        /** required 不是列表时视为无必填参数，放行 */
        @Test
        void shouldProceedWhenRequiredNotList() {
            ToolSpecification spec = ToolSpecification.builder()
                    .name("search").parameters(Map.of("required", "q"))
                    .build();
            ToolInvocation invocation = ToolInvocation.builder().callName("search").build();

            ToolCallHook.Decision decision = hook.before(invocation, contextWithTools(List.of(spec)));

            assertThat(decision.isProceed()).isTrue();
        }

        /** 缺少必填参数时拒绝，并把参数名回喂模型 */
        @Test
        void shouldDenyWhenRequiredArgumentMissing() {
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("search").arguments(Map.of())
                    .build();

            ToolCallHook.Decision decision = hook.before(invocation,
                    contextWithTools(List.of(specWithRequired("search", List.of("query")))));

            assertThat(decision.isDeny()).isTrue();
            assertThat(decision.getReason()).isEqualTo("参数校验失败：缺少必填参数 query");
        }

        /** 必填参数为空白字符串时同样拒绝 */
        @Test
        void shouldDenyWhenRequiredArgumentBlank() {
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("search").arguments(Map.of("query", "  "))
                    .build();

            ToolCallHook.Decision decision = hook.before(invocation,
                    contextWithTools(List.of(specWithRequired("search", List.of("query")))));

            assertThat(decision.isDeny()).isTrue();
        }

        /** 参数表为 null 时按缺失处理并拒绝 */
        @Test
        void shouldDenyWhenArgumentsMapNull() {
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("search").arguments(null)
                    .build();

            ToolCallHook.Decision decision = hook.before(invocation,
                    contextWithTools(List.of(specWithRequired("search", List.of("query")))));

            assertThat(decision.isDeny()).isTrue();
        }

        /** 必填参数齐全时放行，函数名匹配忽略大小写 */
        @Test
        void shouldProceedWhenAllRequiredPresent() {
            ToolInvocation invocation = ToolInvocation.builder()
                    .callName("SEARCH").arguments(Map.of("query", "hello"))
                    .build();

            ToolCallHook.Decision decision = hook.before(invocation,
                    contextWithTools(List.of(specWithRequired("search", List.of("query")))));

            assertThat(decision.isProceed()).isTrue();
        }
    }
}
