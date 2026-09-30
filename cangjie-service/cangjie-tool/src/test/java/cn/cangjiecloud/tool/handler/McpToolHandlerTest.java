package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.mcp.McpClientManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link McpToolHandler} 单元测试：验证 config 中 serverUrl 与 functionName 的解析、
 * 委托 {@link McpClientManager} 调用以及 MCP 调用异常时的失败封装。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MCP 工具处理器 - McpToolHandler")
class McpToolHandlerTest {

    @Mock
    private McpClientManager mcpClientManager;

    private McpToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new McpToolHandler(mcpClientManager);
    }

    private static ToolEntity tool(String functionName, String config) {
        ToolEntity entity = new ToolEntity();
        entity.setId("t1");
        entity.setName("MCP 工具");
        entity.setToolType(ToolConstants.ToolType.MCP);
        entity.setFunctionName(functionName);
        entity.setConfig(config);
        entity.setParameters("{\"type\":\"object\"}");
        return entity;
    }

    @Nested
    @DisplayName("buildToolSpecification")
    class BuildSpecification {

        @Test
        @DisplayName("使用 functionName 作为模型侧函数名")
        void shouldUseFunctionName() {
            ToolSpecification spec = handler.buildToolSpecification(
                    tool("echo", "{\"serverUrl\":\"http://mcp.local\"}"));

            assertThat(spec.getToolId()).isEqualTo("t1");
            assertThat(spec.getName()).isEqualTo("echo");
            assertThat(spec.getDescription()).isEqualTo("MCP 工具");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.MCP);
            assertThat(spec.getParameters()).containsEntry("type", "object");
        }

        @Test
        @DisplayName("functionName 缺失时回退 tool_<id>，参数非法时返回空 Map")
        void shouldFallbackNameAndParameters() {
            ToolEntity entity = tool(null, null);
            entity.setParameters("不是 JSON");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getName()).isEqualTo("tool_t1");
            assertThat(spec.getParameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute 执行")
    class Execute {

        @Test
        @DisplayName("解析 config 中的 serverUrl 并委托 McpClientManager")
        void shouldDelegateToManager() {
            when(mcpClientManager.callTool(eq("http://mcp.local"), eq("echo"), any()))
                    .thenReturn("工具返回内容");

            ToolExecuteResultDTO result = handler.execute(
                    tool("echo", "{\"serverUrl\":\"http://mcp.local\"}"),
                    Map.of("q", "hi"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("工具返回内容");
            assertThat(result.getExecutionTime()).isNotNull();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(mcpClientManager).callTool(eq("http://mcp.local"), eq("echo"), paramsCaptor.capture());
            assertThat(paramsCaptor.getValue()).containsEntry("q", "hi");
        }

        @Test
        @DisplayName("config 非法 JSON 时 serverUrl 退化为空串仍继续调用")
        void shouldTolerateInvalidConfig() {
            when(mcpClientManager.callTool(any(), any(), any())).thenReturn("ok");

            ToolExecuteResultDTO result = handler.execute(tool("echo", "不是 JSON"), Map.of());

            assertThat(result.getSuccess()).isTrue();
            verify(mcpClientManager).callTool("", "echo", Map.of());
        }

        @Test
        @DisplayName("MCP 调用抛异常时返回失败结果")
        void shouldReturnFailureWhenManagerThrows() {
            when(mcpClientManager.callTool(any(), any(), any()))
                    .thenThrow(new RuntimeException("连接失败"));

            ToolExecuteResultDTO result = handler.execute(
                    tool("echo", "{\"serverUrl\":\"http://mcp.local\"}"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("连接失败");
        }

        @Test
        @DisplayName("arguments 为 null 时原样透传")
        void shouldPassNullArguments() {
            when(mcpClientManager.callTool(any(), any(), any())).thenReturn("ok");

            ToolExecuteResultDTO result = handler.execute(tool("echo", "{}"), null);

            assertThat(result.getSuccess()).isTrue();
            verify(mcpClientManager).callTool("", "echo", null);
        }

        @Test
        @DisplayName("config 为空时按空对象处理，toolName 取自 functionName")
        void shouldHandleEmptyConfig() {
            Map<String, Object> config = new HashMap<>();
            when(mcpClientManager.callTool(eq(""), eq(null), any())).thenReturn("ok");

            ToolExecuteResultDTO result = handler.execute(tool(null, null), config);

            assertThat(result.getSuccess()).isTrue();
        }
    }
}
