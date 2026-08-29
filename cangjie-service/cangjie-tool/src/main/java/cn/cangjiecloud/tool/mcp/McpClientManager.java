package cn.cangjiecloud.tool.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
            JSONObject request = new JSONObject();
            request.put("jsonrpc", "2.0");
            request.put("method", "initialize");
            request.put("params", new JSONObject()
                    .fluentPut("protocolVersion", "2024-11-05")
                    .fluentPut("capabilities", new JSONObject())
                    .fluentPut("clientInfo", new JSONObject()
                            .fluentPut("name", "cangjie-agent")
                            .fluentPut("version", "1.0")));
            request.put("id", requestId.getAndIncrement());

            String response = postJson(serverUrl, request.toJSONString());
            JSONObject respJson = JSON.parseObject(response);
            if (respJson.containsKey("error")) {
                throw new IOException("MCP 初始化失败: " + respJson.getJSONObject("error"));
            }
            connected = true;
            log.debug("MCP 握手成功: {}", serverUrl);

            // 发送 initialized 通知
            JSONObject notif = new JSONObject();
            notif.put("jsonrpc", "2.0");
            notif.put("method", "notifications/initialized");
            notif.put("params", new JSONObject());
            try {
                postJson(serverUrl, notif.toJSONString());
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
                JSONObject request = new JSONObject();
                request.put("jsonrpc", "2.0");
                request.put("method", "tools/list");
                request.put("params", new JSONObject());
                request.put("id", requestId.getAndIncrement());

                String response = postJson(serverUrl, request.toJSONString());
                JSONObject respJson = JSON.parseObject(response);
                if (respJson.containsKey("result")) {
                    JSONArray toolsArray = respJson.getJSONObject("result").getJSONArray("tools");
                    if (toolsArray != null) {
                        this.tools = toolsArray.stream()
                                .map(t -> (Map<String, Object>) JSON.parseObject(t.toString()).getInnerMap())
                                .toList();
                        log.info("MCP 工具发现完成: {} -> {} 个工具", serverUrl, tools.size());
                    }
                } else if (respJson.containsKey("error")) {
                    log.warn("MCP tools/list 失败: {} -> {}", serverUrl, respJson.getJSONObject("error"));
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
                JSONObject request = new JSONObject();
                request.put("jsonrpc", "2.0");
                request.put("method", "tools/call");
                request.put("params", new JSONObject()
                        .fluentPut("name", toolName)
                        .fluentPut("arguments", arguments != null ? arguments : Map.of()));
                request.put("id", requestId.getAndIncrement());

                String response = postJson(serverUrl, request.toJSONString());
                JSONObject respJson = JSON.parseObject(response);

                if (respJson.containsKey("result")) {
                    JSONObject result = respJson.getJSONObject("result");
                    Object content = result.get("content");
                    if (content instanceof JSONArray) {
                        return ((JSONArray) content).toJSONString();
                    }
                    return content != null ? content.toString() : "";
                }

                if (respJson.containsKey("error")) {
                    JSONObject err = respJson.getJSONObject("error");
                    return JSON.toJSONString(Map.of(
                            "success", false,
                            "error", err.getOrDefault("message", "unknown").toString()
                    ));
                }

                return response;
            } catch (Exception e) {
                log.error("MCP 工具调用异常: server={}, tool={}", serverUrl, toolName, e);
                return JSON.toJSONString(Map.of(
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