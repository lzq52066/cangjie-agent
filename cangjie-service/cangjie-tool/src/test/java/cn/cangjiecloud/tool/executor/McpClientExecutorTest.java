package cn.cangjiecloud.tool.executor;

import cn.cangjiecloud.tool.mcp.McpClientManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpClientExecutor 单元测试
 * <p>
 * 执行器仅做参数透传，验证委托与返回值传递。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MCP 客户端执行器 - McpClientExecutor")
class McpClientExecutorTest {

    @Mock
    private McpClientManager mcpClientManager;

    private McpClientExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new McpClientExecutor(mcpClientManager);
    }

    /**
     * execute 应把 serverUrl/toolName/arguments 原样委托给 McpClientManager 并返回其结果
     */
    @Test
    void shouldDelegateToMcpClientManager() {
        Map<String, Object> arguments = Map.of("city", "杭州");
        when(mcpClientManager.callTool("http://mcp.local/sse", "get_weather", arguments))
                .thenReturn("{\"temp\":20}");

        String result = executor.execute("http://mcp.local/sse", "get_weather", arguments);

        assertThat(result).isEqualTo("{\"temp\":20}");
        verify(mcpClientManager).callTool("http://mcp.local/sse", "get_weather", arguments);
    }

    /**
     * 参数为 null 时应原样透传，不做额外处理
     */
    @Test
    void shouldPassNullArgumentsThrough() {
        when(mcpClientManager.callTool("http://mcp.local/sse", "ping", null)).thenReturn("pong");

        String result = executor.execute("http://mcp.local/sse", "ping", null);

        assertThat(result).isEqualTo("pong");
        verify(mcpClientManager).callTool("http://mcp.local/sse", "ping", null);
    }
}
