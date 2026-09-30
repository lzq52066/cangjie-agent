package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpToolHandler 单元测试
 * <p>
 * 使用 JDK 内置 HttpServer 在本机随机端口启动 stub 服务拦截请求，
 * 覆盖占位符替换、query 拼接、body 模板与异常兜底，不访问任何外部网络。
 */
@DisplayName("HTTP 工具处理器 - HttpToolHandler")
class HttpToolHandlerTest {

    private static HttpServer server;
    private static String baseUrl;

    /** stub 服务记录的最近一次请求 */
    private static volatile String lastMethod;
    private static volatile String lastQuery;
    private static volatile String lastBody;
    private static volatile com.sun.net.httpserver.Headers lastHeaders;

    /** stub 服务的可配置响应 */
    private static volatile int stubStatus;
    private static volatile String stubResponse;

    private HttpToolHandler handler;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", HttpToolHandlerTest::handleEcho);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handleEcho(HttpExchange exchange) throws IOException {
        lastMethod = exchange.getRequestMethod();
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
        handler = new HttpToolHandler();
        lastMethod = null;
        lastQuery = null;
        lastBody = null;
        lastHeaders = null;
        stubStatus = 200;
        stubResponse = "{\"ok\":true}";
    }

    /**
     * 构造 HTTP 工具实体
     */
    private static ToolEntity tool(String id, String config) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setName("天气查询");
        entity.setToolType(ToolConstants.ToolType.HTTP);
        entity.setStatus(ToolConstants.STATUS_ACTIVE);
        entity.setConfig(config);
        return entity;
    }

    /**
     * 构造 JSON 配置
     */
    private static String config(Map<String, Object> values) {
        return JsonUtils.toJSONString(values);
    }

    @Nested
    @DisplayName("buildToolSpecification - 构建工具规格")
    class BuildToolSpecification {

        /**
         * 合法 functionName 作为模型侧函数名，参数定义解析为 Map
         */
        @Test
        void shouldBuildSpecFromEntity() {
            ToolEntity entity = tool("t1", "{}");
            entity.setFunctionName("query_weather");
            entity.setDescription("查询天气");
            entity.setParameters("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getName()).isEqualTo("query_weather");
            assertThat(spec.getToolId()).isEqualTo("t1");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.HTTP);
            assertThat(spec.getDescription()).isEqualTo("查询天气");
            assertThat(spec.getParameters()).containsKey("type");
        }

        /**
         * 未配置 functionName 时回退为 tool_<id>，描述回退为名称，参数为空 Map
         */
        @Test
        void shouldFallbackNameAndDescription() {
            ToolSpecification spec = handler.buildToolSpecification(tool("t9", "{}"));

            assertThat(spec.getName()).isEqualTo("tool_t9");
            assertThat(spec.getDescription()).isEqualTo("天气查询");
            assertThat(spec.getParameters()).isEmpty();
        }

        /**
         * 参数定义不是合法 JSON 时按空参数处理，不抛异常
         */
        @Test
        void shouldTolerateBrokenParametersJson() {
            ToolEntity entity = tool("t1", "{}");
            entity.setParameters("{不是 JSON");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getParameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute - GET 请求")
    class ExecuteGet {

        /**
         * URL 中的占位符被参数替换，响应体原样返回
         */
        @Test
        void shouldResolvePlaceholdersInUrl() {
            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo?q={{q}}&city={{city}}"))),
                    Map.of("q", "hello", "city", "上海"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("{\"ok\":true}");
            assertThat(result.getExecutionTime()).isNotNull();
            assertThat(lastMethod).isEqualTo("GET");
            assertThat(lastQuery).contains("q=hello").contains("city=上海");
        }

        /**
         * 未配置 body 模板时所有参数自动拼接到 query
         */
        @Test
        void shouldAppendParamsToQuery() {
            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo", "method", "get"))),
                    Map.of("a", "1", "b", 2));

            assertThat(result.getSuccess()).isTrue();
            assertThat(lastMethod).isEqualTo("GET");
            assertThat(lastQuery).contains("a=1").contains("b=2");
            assertThat(lastBody).isEmpty();
        }

        /**
         * 未解析的占位符整段参数被清除，首个 & 修正为 ?
         */
        @Test
        void shouldDropUnresolvedPlaceholderParams() {
            handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo?first={{first}}&second=2"))),
                    Map.of());

            assertThat(lastQuery).isEqualTo("second=2");
        }

        /**
         * body 模板中已引用的参数不再重复拼到 query
         */
        @Test
        void shouldSkipParamsReferencedByBodyTemplate() {
            handler.execute(
                    tool("t1", config(Map.of(
                            "url", baseUrl + "/echo",
                            "body", "{\"q\":\"{{q}}\"}"))),
                    Map.of("q", "hello", "extra", "x"));

            assertThat(lastMethod).isEqualTo("GET");
            assertThat(lastBody).isEmpty();
            assertThat(lastQuery).isEqualTo("extra=x");
        }

        /**
         * 配置的请求头透传到目标服务
         */
        @Test
        void shouldPassConfiguredHeaders() {
            handler.execute(
                    tool("t1", config(Map.of(
                            "url", baseUrl + "/echo",
                            "headers", Map.of("X-Token", "abc", "X-Trace", "t1")))),
                    Map.of());

            assertThat(lastHeaders.getFirst("X-Token")).isEqualTo("abc");
            assertThat(lastHeaders.getFirst("X-Trace")).isEqualTo("t1");
        }

        /**
         * params 为 null 时不抛异常，且不携带 query
         */
        @Test
        void shouldTolerateNullParams() {
            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo"))), null);

            assertThat(result.getSuccess()).isTrue();
            assertThat(lastQuery).isNull();
        }
    }

    @Nested
    @DisplayName("execute - POST 请求")
    class ExecutePost {

        /**
         * 未配置 body 模板且非 GET/DELETE 时，参数整体序列化为 JSON body，query 置空
         */
        @Test
        void shouldSendParamsAsJsonBodyWhenNoTemplate() {
            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo", "method", "post"))),
                    Map.of("name", "小明"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(lastMethod).isEqualTo("POST");
            assertThat(lastQuery).isNull();
            Map<String, Object> body = JsonUtils.parseMap(lastBody);
            assertThat(body).containsEntry("name", "小明");
        }

        /**
         * 配置 body 模板时按占位符替换，未引用参数仍拼到 query
         */
        @Test
        void shouldUseBodyTemplateAndKeepOtherParamsInQuery() {
            handler.execute(
                    tool("t1", config(Map.of(
                            "url", baseUrl + "/echo",
                            "method", "POST",
                            "body", "{\"name\":\"{{name}}\"}"))),
                    Map.of("name", "小明", "tag", "go"));

            assertThat(lastMethod).isEqualTo("POST");
            assertThat(lastBody).isEqualTo("{\"name\":\"小明\"}");
            assertThat(lastQuery).isEqualTo("tag=go");
        }

        /**
         * body 配置为 JSON 对象时序列化为模板
         */
        @Test
        void shouldSerializeObjectBodyConfig() {
            Map<String, Object> bodyConfig = new HashMap<>();
            bodyConfig.put("k", 1);

            handler.execute(
                    tool("t1", config(Map.of(
                            "url", baseUrl + "/echo",
                            "method", "PUT",
                            "body", bodyConfig))),
                    Map.of());

            assertThat(lastMethod).isEqualTo("PUT");
            assertThat(lastBody).isEqualTo("{\"k\":1}");
        }
    }

    @Nested
    @DisplayName("execute - 异常兜底")
    class ExecuteFailure {

        /**
         * URL 非法导致底层解析异常时返回 success=false 而不抛出
         */
        @Test
        void shouldReturnFailureWhenUrlInvalid() {
            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", "http://bad host/echo"))), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isNotBlank();
        }

        /**
         * config 不是合法 JSON 时按空配置处理，同样返回 failure 结果
         */
        @Test
        void shouldReturnFailureWhenConfigBroken() {
            ToolExecuteResultDTO result = handler.execute(tool("t1", "{不是 JSON"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getExecutionTime()).isNotNull();
        }

        /**
         * 非 2xx 响应由执行器包装为 JSON 文本返回，处理结果保留错误详情
         */
        @Test
        void shouldExposeNon2xxDetailInOutput() {
            stubStatus = 500;
            stubResponse = "internal error";

            ToolExecuteResultDTO result = handler.execute(
                    tool("t1", config(Map.of("url", baseUrl + "/echo"))), Map.of());

            String output = String.valueOf(result.getOutput());
            assertThat(output).contains("\"status\":500").contains("internal error");
        }
    }
}
