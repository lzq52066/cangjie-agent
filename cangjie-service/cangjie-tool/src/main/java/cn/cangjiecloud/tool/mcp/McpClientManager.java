package cn.cangjiecloud.tool.mcp;

import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端管理器
 * <p>
 * 按 serverUrl 缓存 MCP 会话（HTTP 连接 + 连接状态），避免每次工具调用都重新握手。
 * 使用 OkHttp 连接池复用底层 TCP 连接，符合 MCP JSON-RPC 2.0 协议。
 * <p>
 * 未来可平滑迁移至 {@code io.modelcontextprotocol:java-sdk}：
 * 将内部 OkHttp 调用替换为 SDK 的 {@code McpSyncClient}。
 */
@Slf4j
@Component
public class McpClientManager {

    /**
     * 已缓存的 MCP 会话：key = serverUrl，value = McpSession
     */
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    /**
     * 共享 HTTP 客户端（连接池复用）
     */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
            .build();

    /**
     * 获取或创建 MCP 会话（懒初始化 + 缓存）
     *
     * @param serverUrl MCP 服务地址
     * @return 已初始化的会话
     */
    public McpSession getOrCreateSession(String serverUrl) {
        return sessions.computeIfAbsent(serverUrl, url -> {
            McpSession session = new McpSession(url);
            try {
                session.initialize();
                log.info("MCP 会话已建立: {}", url);
            } catch (Exception e) {
                log.warn("MCP 初始化握手失败: {}, {}", url, e.getMessage());
                // 即使初始化失败也缓存，避免频繁重试
                session.setConnected(false);
            }
            return session;
        });
    }

    /**
     * 调用 MCP 工具
     *
     * @param serverUrl MCP 服务地址
     * @param toolName  工具名称
     * @param arguments 调用参数
     * @return 执行结果 JSON 字符串
     */
    public String callTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        McpSession session = getOrCreateSession(serverUrl);
        return session.callTool(toolName, arguments);
    }

    /**
     * 获取 MCP 服务的工具列表（自动发现）
     *
     * @param serverUrl MCP 服务地址
     * @param refresh   是否强制刷新（默认读取缓存）
     * @return 工具列表
     */
    public List<Map<String, Object>> listTools(String serverUrl, boolean refresh) {
        McpSession session = getOrCreateSession(serverUrl);
        if (refresh || session.getTools() == null) {
            session.discoverTools();
        }
        return session.getTools() != null ? session.getTools() : Collections.emptyList();
    }

    /**
     * 失效指定服务的会话缓存
     */
    public void evictSession(String serverUrl) {
        sessions.remove(serverUrl);
        log.info("MCP 会话已失效: {}", serverUrl);
    }

    /**
     * 测试与 MCP 服务的连通性：强制重新握手
     *
     * @return true 握手成功
     */
    public boolean testConnection(String serverUrl) {
        sessions.remove(serverUrl);
        McpSession session = getOrCreateSession(serverUrl);
        return session.isConnected();
    }

    /**
     * MCP 会话封装
     * <p>
     * 维护与单个 MCP 服务器的连接状态、tools/list 缓存和请求 id 计数。
     */
    public class McpSession {

        private final String serverUrl;
        private volatile boolean connected;
        private final AtomicLong requestId = new AtomicLong(1);
        private List<Map<String, Object>> tools;

        McpSession(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        boolean isConnected() {
            return connected;
        }

        void setConnected(boolean connected) {
            this.connected = connected;
        }

        List<Map<String, Object>> getTools() {
            return tools;
        }

        /**
         * MCP 初始化握手 (JSON-RPC 2.0 initialize)
         */
        void initialize() throws IOException {
            ObjectNode request = JsonUtils.newObject();
            request.put("jsonrpc", "2.0");
            request.put("method", "initialize");
            ObjectNode params = JsonUtils.newObject();
            params.put("protocolVersion", "2024-11-05");
            params.set("capabilities", JsonUtils.newObject());
            ObjectNode clientInfo = JsonUtils.newObject();
            clientInfo.put("name", "cangjie-agent");
            clientInfo.put("version", "1.0");
            params.set("clientInfo", clientInfo);
            request.set("params", params);
            request.put("id", requestId.getAndIncrement());

            String response = postJson(serverUrl, request.toString());
            ObjectNode respJson = JsonUtils.parseObject(response);
            if (respJson.has("error")) {
                throw new IOException("MCP 初始化失败: " + respJson.get("error"));
            }
            connected = true;
            log.debug("MCP 握手成功: {}", serverUrl);

            // 发送 initialized 通知
            ObjectNode notif = JsonUtils.newObject();
            notif.put("jsonrpc", "2.0");
            notif.put("method", "notifications/initialized");
            notif.set("params", JsonUtils.newObject());
            try {
                postJson(serverUrl, notif.toString());
            } catch (Exception e) {
                log.debug("MCP initialized 通知发送失败（非致命）: {}", e.getMessage());
            }
        }

        /**
         * 自动发现工具列表 (tools/list)
         */
        @SuppressWarnings("unchecked")
        void discoverTools() {
            try {
                ObjectNode request = JsonUtils.newObject();
                request.put("jsonrpc", "2.0");
                request.put("method", "tools/list");
                request.set("params", JsonUtils.newObject());
                request.put("id", requestId.getAndIncrement());

                String response = postJson(serverUrl, request.toString());
                ObjectNode respJson = JsonUtils.parseObject(response);
                if (respJson.has("result")) {
                    JsonNode toolsNode = respJson.get("result").get("tools");
                    if (toolsNode != null && toolsNode.isArray()) {
                        List<Map<String, Object>> discovered = new ArrayList<>(toolsNode.size());
                        for (JsonNode node : toolsNode) {
                            discovered.add((Map<String, Object>) JsonUtils.toObject(node, Map.class));
                        }
                        this.tools = discovered;
                        log.info("MCP 工具发现完成: {} -> {} 个工具", serverUrl, tools.size());
                    }
                } else if (respJson.has("error")) {
                    log.warn("MCP tools/list 失败: {} -> {}", serverUrl, respJson.get("error"));
                }
            } catch (Exception e) {
                log.warn("MCP 工具发现异常: {} -> {}", serverUrl, e.getMessage());
            }
        }

        /**
         * 调用 MCP 工具 (tools/call)
         */
        String callTool(String toolName, Map<String, Object> arguments) {
            try {
                ObjectNode request = JsonUtils.newObject();
                request.put("jsonrpc", "2.0");
                request.put("method", "tools/call");
                ObjectNode params = JsonUtils.newObject();
                params.put("name", toolName);
                params.set("arguments", JsonUtils.mapper().valueToTree(arguments != null ? arguments : Map.of()));
                request.set("params", params);
                request.put("id", requestId.getAndIncrement());

                String response = postJson(serverUrl, request.toString());
                ObjectNode respJson = JsonUtils.parseObject(response);

                if (respJson.has("result")) {
                    JsonNode content = respJson.get("result").get("content");
                    if (content == null || content.isNull()) {
                        return "";
                    }
                    return content.isTextual() ? content.asText() : content.toString();
                }

                if (respJson.has("error")) {
                    JsonNode message = respJson.get("error").get("message");
                    return JsonUtils.toJSONString(Map.of(
                            "success", false,
                            "error", message != null ? message.asText() : "unknown"
                    ));
                }

                return response;
            } catch (Exception e) {
                log.error("MCP 工具调用异常: server={}, tool={}", serverUrl, toolName, e);
                return JsonUtils.toJSONString(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ));
            }
        }

        /**
         * HTTP POST JSON 请求
         */
        private String postJson(String url, String json) throws IOException {
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + ": " + response.message());
                }
                return response.body() != null ? response.body().string() : "{}";
            }
        }
    }
}