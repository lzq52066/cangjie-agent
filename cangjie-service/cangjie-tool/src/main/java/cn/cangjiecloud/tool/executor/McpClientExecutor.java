package cn.cangjiecloud.tool.executor;

import cn.cangjiecloud.tool.mcp.McpClientManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MCP 客户端执行器
 * <p>
 * 委托 {@link McpClientManager} 管理 MCP 会话缓存与 JSON-RPC 协议交互。
 * 按 serverUrl 复用已握手的会话，避免每次工具调用都重新建立连接。
 */
@Slf4j
public class McpClientExecutor {

    private final McpClientManager mcpClientManager;

    public McpClientExecutor(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    public String execute(String serverUrl, String toolName, Map<String, Object> arguments) {
        log.info("MCP 工具调用: server={}, tool={}", serverUrl, toolName);
        return mcpClientManager.callTool(serverUrl, toolName, arguments);
    }
}