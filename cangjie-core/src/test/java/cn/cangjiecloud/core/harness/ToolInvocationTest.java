package cn.cangjiecloud.core.harness;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolInvocation} 单元测试。
 */
class ToolInvocationTest {

    @Test
    void argShouldReturnNullWhenArgumentsNotSet() {
        assertThat(ToolInvocation.builder().build().arg("city")).isNull();
    }

    @Test
    void argShouldReturnNullWhenKeyAbsent() {
        ToolInvocation invocation = ToolInvocation.builder().arguments(Map.of("a", "b")).build();

        assertThat(invocation.arg("missing")).isNull();
    }

    @Test
    void argShouldConvertValueToString() {
        ToolInvocation invocation = ToolInvocation.builder()
                .arguments(Map.of("city", "hangzhou", "count", 3, "flag", true))
                .build();

        assertThat(invocation.arg("city")).isEqualTo("hangzhou");
        assertThat(invocation.arg("count")).isEqualTo("3");
        assertThat(invocation.arg("flag")).isEqualTo("true");
    }
}
