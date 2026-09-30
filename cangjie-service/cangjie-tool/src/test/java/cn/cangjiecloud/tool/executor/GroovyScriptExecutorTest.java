package cn.cangjiecloud.tool.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GroovyScriptExecutor 单元测试
 * <p>
 * 使用真实 Groovy 运行时执行简单脚本，断言沙箱行为与结果包装（无网络、无数据库依赖）。
 */
@DisplayName("Groovy 脚本执行器 - GroovyScriptExecutor")
class GroovyScriptExecutorTest {

    private GroovyScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new GroovyScriptExecutor();
    }

    @Nested
    @DisplayName("execute - 正常执行")
    class Execute {

        /**
         * 简单表达式应执行成功并返回计算结果
         */
        @Test
        void shouldEvaluateSimpleExpression() {
            GroovyScriptExecutor.ScriptResult result = executor.execute("1 + 1", Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo(2);
            assertThat(result.error()).isNull();
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
        }

        /**
         * 参数应绑定为脚本变量
         */
        @Test
        void shouldBindParametersAsVariables() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("a + b", Map.of("a", 40, "b", 2));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo(42);
        }

        /**
         * params 为 null 时脚本仍可执行
         */
        @Test
        void shouldRunWithNullParams() {
            GroovyScriptExecutor.ScriptResult result = executor.execute("'cangjie'.toUpperCase()", null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo("CANGJIE");
        }

        /**
         * 多行脚本（变量声明 + 运算）应正常执行
         */
        @Test
        void shouldRunMultiLineScript() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("def x = 5\nx * 2", Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo(10);
        }

        /**
         * 沙箱允许闭包（closuresAllowed = true）
         */
        @Test
        void shouldAllowClosures() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("def doubler = { it * 2 }\ndoubler(21)", Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo(42);
        }

        /**
         * 脚本返回集合类型时结果原样透出
         */
        @Test
        void shouldReturnCollectionResult() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("[1, 2, 3].sum()", Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.result()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("execute - 异常与沙箱")
    class ExecuteFailure {

        /**
         * 脚本运行期异常应被包装为失败结果，错误信息包含原始异常消息
         */
        @Test
        void shouldReturnErrorWhenScriptThrows() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("throw new RuntimeException('脚本异常消息')", Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.result()).isNull();
            assertThat(result.error()).contains("脚本异常消息");
        }

        /**
         * 语法错误脚本应返回失败结果而非抛出异常
         */
        @Test
        void shouldReturnErrorWhenSyntaxInvalid() {
            GroovyScriptExecutor.ScriptResult result = executor.execute("1 +* 2", Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isNotBlank();
        }

        /**
         * 沙箱禁止方法定义（methodDefinitionAllowed = false）
         */
        @Test
        void shouldRejectMethodDefinition() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("def foo() { 1 }\nfoo()", Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).containsIgnoringCase("not allowed");
        }

        /**
         * 沙箱只放行白名单导入，java.io.File 应被拒绝
         */
        @Test
        void shouldRejectNonWhitelistedImport() {
            GroovyScriptExecutor.ScriptResult result =
                    executor.execute("import java.io.File\nnew File('/tmp').getName()", Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isNotNull();
        }
    }
}
