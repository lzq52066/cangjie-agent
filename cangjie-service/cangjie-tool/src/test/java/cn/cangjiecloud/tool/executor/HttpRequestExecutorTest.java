package cn.cangjiecloud.tool.executor;

import cn.cangjiecloud.common.util.JsonUtils;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpRequestExecutor 单元测试
 * <p>
 * 使用 JDK 内置 HttpServer 在本机随机端口启动 stub 服务，拦截并记录请求；
 * 不访问任何外部网络。
 */
@DisplayName("HTTP 工具执行器 - HttpRequestExecutor")
class HttpRequestExecutorTest {

    private static HttpServer server;
    private static String baseUrl;

    /** stub 服务记录的最近一次请求 */
    private static volatile String lastMethod;
    private static volatile String lastPath;
    private static volatile String lastQuery;
    private static volatile String lastBody;
    private static volatile com.sun.net.httpserver.Headers lastHeaders;

    /** stub 服务的可配置响应 */
    private static volatile int stubStatus;
    private static volatile String stubResponse;

    private HttpRequestExecutor executor;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", HttpRequestExecutorTest::handleEcho);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handleEcho(HttpExchange exchange) throws IOException {
        lastMethod = exchange.getRequestMethod();
        lastPath = exchange.getRequestURI().getPath();
        lastQuery = exchange.getRequestURI().getQuery();
        lastHeaders = exchange.getRequestHeaders();
        lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] resp = stubResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(stubStatus, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.close();
    }

    @BeforeEach
    void setUp() {
        executor = new HttpRequestExecutor();
        lastMethod = null;
        lastPath = null;
        lastQuery = null;
        lastBody = null;
        lastHeaders = null;
        stubStatus = 200;
        stubResponse = "{\"ok\":true}";
    }

    @Nested
    @DisplayName("execute - 正常请求")
    class NormalRequest {

        /**
         * GET 请求：查询参数应拼接到 URL 上，响应体原样返回
         */
        @Test
        void shouldSendGetWithQueryParams() {
            String response = executor.execute(baseUrl + "/echo", "GET", null, null,
                    Map.of("a", "1", "b", "2"));

            assertThat(response).isEqualTo("{\"ok\":true}");
            assertThat(lastMethod).isEqualTo("GET");
            assertThat(lastPath).isEqualTo("/echo");
            assertThat(lastQuery).contains("a=1").contains("b=2");
            assertThat(lastBody).isEmpty();
        }

        /**
         * method 为 null 时默认按 GET 发送
         */
        @Test
        void shouldDefaultToGetWhenMethodNull() {
            String response = executor.execute(baseUrl + "/echo", null, null, null, null);

            assertThat(response).isEqualTo("{\"ok\":true}");
            assertThat(lastMethod).isEqualTo("GET");
        }

        /**
         * 请求头应透传到目标服务
         */
        @Test
        void shouldPassRequestHeaders() {
            executor.execute(baseUrl + "/echo", "GET",
                    Map.of("X-Token", "abc", "X-Trace", "t1"), null, null);

            assertThat(lastHeaders.getFirst("X-Token")).isEqualTo("abc");
            assertThat(lastHeaders.getFirst("X-Trace")).isEqualTo("t1");
        }

        /**
         * POST 请求应携带请求体并设置 JSON Content-Type
         */
        @Test
        void shouldSendPostWithBody() {
            String response = executor.execute(baseUrl + "/echo", "post",
                    Map.of("Content-Type", "application/json"), "{\"k\":1}", null);

            assertThat(response).isEqualTo("{\"ok\":true}");
            assertThat(lastMethod).isEqualTo("POST");
            assertThat(lastBody).isEqualTo("{\"k\":1}");
        }

        /**
         * POST 未提供 body 时使用默认空对象 "{}"
         */
        @Test
        void shouldUseDefaultBodyForPost() {
            executor.execute(baseUrl + "/echo", "POST", null, null, null);

            assertThat(lastMethod).isEqualTo("POST");
            assertThat(lastBody).isEqualTo("{}");
        }

        /**
         * PUT 请求应携带请求体
         */
        @Test
        void shouldSendPutWithBody() {
            executor.execute(baseUrl + "/echo", "PUT", null, "{\"u\":2}", null);

            assertThat(lastMethod).isEqualTo("PUT");
            assertThat(lastBody).isEqualTo("{\"u\":2}");
        }

        /**
         * DELETE 无 body 时不发送请求体
         */
        @Test
        void shouldSendDeleteWithoutBody() {
            executor.execute(baseUrl + "/echo", "DELETE", null, null, null);

            assertThat(lastMethod).isEqualTo("DELETE");
            assertThat(lastBody).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute - 异常响应")
    class ErrorResponse {

        /**
         * 非 2xx 响应应包装为带 status/success/error 的 JSON
         */
        @Test
        void shouldWrapNon2xxResponse() {
            stubStatus = 500;
            stubResponse = "internal error";

            String response = executor.execute(baseUrl + "/echo", "GET", null, null, null);

            Map<String, Object> json = JsonUtils.parseMap(response);
            assertThat(json.get("status")).isEqualTo(500);
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("internal error");
        }

        /**
         * 连接失败（端口无服务）应返回 success=false 的 JSON，且不抛异常
         */
        @Test
        void shouldWrapIoException() {
            String response = executor.execute("http://127.0.0.1:1/echo", "GET", null, null, null);

            Map<String, Object> json = JsonUtils.parseMap(response);
            assertThat(json.get("success")).isEqualTo(false);
            assertThat((String) json.get("error")).isNotBlank();
        }
    }
}
