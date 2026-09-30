package cn.cangjiecloud.tool.mcp;

import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpClientManager 单元测试
 * <p>
 * 使用 JDK 内置 HttpServer 模拟 MCP 服务端（JSON-RPC 2.0 协议），
 * 覆盖握手、通知、工具发现、工具调用与连接缓存/失效等流程（不访问外部网络）。
 */
@DisplayName("MCP 客户端管理器 - McpClientManager")
class McpClientManagerTest {

    private static HttpServer server;
    private static String serverUrl;

    /** stub 服务端收到的请求方法序列 */
    private static final List<String> receivedMethods = Collections.synchronizedList(new ArrayList<>());
    /** stub 服务端收到的最近一次 tools/call 请求体 */
    private static volatile ObjectNode lastCallRequest;

    /** stub 服务端行为开关 */
    private static volatile boolean initFailure;
    private static volatile boolean http500;
    private static volatile String callMode;

    private McpClientManager manager;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", McpClientManagerTest::handleMcp);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handleMcp(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ObjectNode request = JsonUtils.parseObject(body);
        String method = request.path("method").asText("");
        receivedMethods.add(method);

        if (http500) {
            byte[] err = "server error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, err.length);
            exchange.getResponseBody().write(err);
            exchange.close();
            return;
        }

        ObjectNode response = JsonUtils.newObject();
        response.put("jsonrpc", "2.0");
        response.put("id", request.path("id").asLong());
        switch (method) {
            case "initialize":
                if (initFailure) {
                    ObjectNode error = JsonUtils.newObject();
                    error.put("code", -32600);
                    error.put("message", "init fail");
                    response.set("error", error);
                } else {
                    response.set("result", JsonUtils.newObject());
                }
                break;
            case "tools/list":
                ObjectNode result = JsonUtils.newObject();
                result.set("tools", JsonUtils.parseArray(
                        "[{\"name\":\"echo\",\"description\":\"回显\"},"
                                + "{\"name\":\"add\",\"description\":\"求和\"}]"));
                response.set("result", result);
                break;
            case "tools/call":
                lastCallRequest = request;
                buildCallResponse(response);
                break;
            default:
                // notifications/initialized：无 id 的通知，直接返回空对象
                response = JsonUtils.newObject();
                break;
        }

        byte[] resp = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.close();
    }

    private static void buildCallResponse(ObjectNode response) {
        switch (callMode) {
            case "text":
                ObjectNode textResult = JsonUtils.newObject();
                textResult.put("content", "hello from mcp");
                response.set("result", textResult);
                break;
            case "array":
                ObjectNode arrayResult = JsonUtils.newObject();
                arrayResult.set("content", JsonUtils.parseArray(
                        "[{\"type\":\"text\",\"text\":\"hi\"}]"));
                response.set("result", arrayResult);
                break;
            case "nullContent":
                ObjectNode nullResult = JsonUtils.newObject();
                nullResult.set("content", null);
                response.set("result", nullResult);
                break;
            case "error":
                ObjectNode err = JsonUtils.newObject();
                err.put("code", -32001);
                err.put("message", "boom tool");
                response.set("error", err);
                break;
            default:
                // 无 result / error 的响应
                break;
        }
    }

    @BeforeEach
    void setUp() {
        manager = new McpClientManager();
        receivedMethods.clear();
        lastCallRequest = null;
        initFailure = false;
        http500 = false;
        callMode = "text";
    }

    /**
     * 统计指定方法被调用的次数
     */
    private static long countMethod(String method) {
        synchronized (receivedMethods) {
            return receivedMethods.stream().filter(method::equals).count();
        }
    }

    @Nested
    @DisplayName("testConnection - 连通性测试")
    class TestConnection {

        /**
         * 服务端正常响应 initialize 时返回 true，并额外发送 initialized 通知
         */
        @Test
        void shouldReturnTrueWhenHandshakeSucceeds() {
            boolean connected = manager.testConnection(serverUrl);

            assertThat(connected).isTrue();
            assertThat(countMethod("initialize")).isEqualTo(1);
            assertThat(countMethod("notifications/initialized")).isEqualTo(1);
        }

        /**
         * 服务端返回 JSON-RPC error 时握手失败返回 false
         */
        @Test
        void shouldReturnFalseWhenServerReturnsError() {
            initFailure = true;

            boolean connected = manager.testConnection(serverUrl);

            assertThat(connected).isFalse();
        }

        /**
         * 服务端返回 HTTP 500 时握手失败返回 false
         */
        @Test
        void shouldReturnFalseWhenHttp500() {
            http500 = true;

            boolean connected = manager.testConnection(serverUrl);

            assertThat(connected).isFalse();
        }

        /**
         * testConnection 先失效旧会话再重建：每次都会重新握手
         */
        @Test
        void shouldForceRehandshake() {
            manager.testConnection(serverUrl);
            manager.testConnection(serverUrl);

            assertThat(countMethod("initialize")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getOrCreateSession - 会话缓存")
    class GetOrCreateSession {

        /**
         * 同一 serverUrl 重复获取应返回同一会话实例且只握手一次
         */
        @Test
        void shouldCacheSessionByUrl() {
            McpClientManager.McpSession first = manager.getOrCreateSession(serverUrl);
            McpClientManager.McpSession second = manager.getOrCreateSession(serverUrl);

            assertThat(second).isSameAs(first);
            assertThat(countMethod("initialize")).isEqualTo(1);
        }

        /**
         * evictSession 后再获取应创建新会话并重新握手
         */
        @Test
        void shouldRecreateAfterEvict() {
            McpClientManager.McpSession first = manager.getOrCreateSession(serverUrl);
            manager.evictSession(serverUrl);
            McpClientManager.McpSession second = manager.getOrCreateSession(serverUrl);

            assertThat(second).isNotSameAs(first);
            assertThat(countMethod("initialize")).isEqualTo(2);
        }

        /**
         * 初始化失败（HTTP 500）时仍缓存会话且 connected=false，避免频繁重试
         */
        @Test
        void shouldCacheFailedSession() {
            http500 = true;

            McpClientManager.McpSession first = manager.getOrCreateSession(serverUrl);
            McpClientManager.McpSession second = manager.getOrCreateSession(serverUrl);

            assertThat(second).isSameAs(first);
            assertThat(first.isConnected()).isFalse();
            assertThat(countMethod("initialize")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("callTool - 工具调用")
    class CallTool {

        /**
         * result.content 为字符串时直接返回文本
         */
        @Test
        void shouldReturnTextualContent() {
            String result = manager.callTool(serverUrl, "echo", Map.of("msg", "hi"));

            assertThat(result).isEqualTo("hello from mcp");
            assertThat(lastCallRequest.path("method").asText()).isEqualTo("tools/call");
            assertThat(lastCallRequest.path("params").path("name").asText()).isEqualTo("echo");
            assertThat(lastCallRequest.path("params").path("arguments").path("msg").asText())
                    .isEqualTo("hi");
            assertThat(lastCallRequest.path("jsonrpc").asText()).isEqualTo("2.0");
        }

        /**
         * result.content 为数组时返回其 JSON 序列化结果
         */
        @Test
        void shouldReturnStructuredContentAsJson() {
            callMode = "array";

            String result = manager.callTool(serverUrl, "echo", Map.of());

            assertThat(result).contains("\"type\":\"text\"").contains("\"hi\"");
        }

        /**
         * result.content 为 null 时返回空字符串
         */
        @Test
        void shouldReturnEmptyStringWhenContentNull() {
            callMode = "nullContent";

            String result = manager.callTool(serverUrl, "echo", Map.of());

            assertThat(result).isEmpty();
        }

        /**
         * 服务端返回 JSON-RPC error 时包装为失败 JSON
         */
        @Test
        void shouldWrapJsonRpcError() {
            callMode = "error";

            String result = manager.callTool(serverUrl, "echo", Map.of());

            Map<String, Object> json = JsonUtils.parseMap(result);
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("boom tool");
        }

        /**
         * arguments 为 null 时按空对象发送
         */
        @Test
        void shouldSendEmptyArgumentsWhenNull() {
            manager.callTool(serverUrl, "echo", null);

            JsonNode arguments = lastCallRequest.path("params").path("arguments");
            assertThat(arguments.isObject()).isTrue();
            assertThat(arguments.isEmpty()).isTrue();
        }

        /**
         * HTTP 500 导致协议异常时返回 success=false 的 JSON，且不抛异常
         */
        @Test
        void shouldWrapTransportException() {
            http500 = true;

            String result = manager.callTool(serverUrl, "echo", Map.of());

            Map<String, Object> json = JsonUtils.parseMap(result);
            assertThat(json.get("success")).isEqualTo(false);
            assertThat((String) json.get("error")).contains("HTTP 500");
        }
    }

    @Nested
    @DisplayName("listTools - 工具发现与缓存")
    class ListTools {

        /**
         * 首次发现应返回服务端工具列表
         */
        @Test
        void shouldDiscoverTools() {
            List<Map<String, Object>> tools = manager.listTools(serverUrl, false);

            assertThat(tools).hasSize(2);
            assertThat(tools.get(0)).containsEntry("name", "echo");
            assertThat(tools.get(1)).containsEntry("name", "add");
            assertThat(countMethod("tools/list")).isEqualTo(1);
        }

        /**
         * 非 refresh 且已有缓存时不再请求服务端
         */
        @Test
        void shouldUseCacheWhenNotRefreshing() {
            manager.listTools(serverUrl, false);
            List<Map<String, Object>> again = manager.listTools(serverUrl, false);

            assertThat(again).hasSize(2);
            assertThat(countMethod("tools/list")).isEqualTo(1);
        }

        /**
         * refresh=true 时强制重新发现
         */
        @Test
        void shouldRefreshWhenRequested() {
            manager.listTools(serverUrl, false);
            manager.listTools(serverUrl, true);

            assertThat(countMethod("tools/list")).isEqualTo(2);
        }

        /**
         * 工具发现失败（HTTP 500）时返回空列表且不抛异常
         */
        @Test
        void shouldReturnEmptyListWhenDiscoveryFails() {
            http500 = true;

            List<Map<String, Object>> tools = manager.listTools(serverUrl, false);

            assertThat(tools).isEmpty();
        }
    }
}
