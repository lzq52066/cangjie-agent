package cn.cangjiecloud.tool.executor;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MCP 客户端执行器 — 简化版，通过 HTTP 调用 MCP 工具
 * <p>
 * 完整 MCP 协议集成需要 langchain4j-mcp 或直接使用 mcp.io 协议栈，
 * 此简化版通过 HTTP POST 调用 MCP 服务端暴露的工具端点。
 */
@Slf4j
public class McpClientExecutor {

    public String execute(String serverUrl, String toolName, Map<String, Object> arguments) {
        // 简化版：HTTP 调用 MCP 服务
        // 实际生产环境应使用 langchain4j-mcp 或 stdio 协议集成
        log.info("MCP 工具调用: server={}, tool={}, args={}", serverUrl, toolName, arguments);

        try {
            // 构建 MCP JSON-RPC 请求
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "method", "tools/call",
                    "params", Map.of(
                            "name", toolName,
                            "arguments", arguments
                    ),
                    "id", 1
            );

            String result = new HttpRequestExecutor().execute(
                    serverUrl, "POST", Map.of("Content-Type", "application/json"),
                    JSON.toJSONString(request), null
            );
            return result;
        } catch (Exception e) {
            log.error("MCP 工具执行失败: server={}, tool={}", serverUrl, toolName, e);
            return JSON.toJSONString(Map.of(
                    "success", false,
                    "error", "MCP 工具执行失败: " + e.getMessage()
            ));
        }
    }
}