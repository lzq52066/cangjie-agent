package cn.cangjiecloud.observability.exporter;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.observability.ExportedSpan;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link OtlpHttpTraceExporter} 单元测试。
 * <p>
 * 重点钉三件容易写错的事：OTLP 的 JSON 结构（resourceSpans / scopeSpans / 定长 hex ID /
 * 纳秒时间必须是字符串）、gen_ai 语义投影、以及「出口不能把正文带出去除非显式开启」。
 * 发送路径用 JDK 自带的 HttpServer 接一个本地端口，只为验证真的按 OTLP 路径与头部发出去了。
 */
class OtlpHttpTraceExporterTest {

    private static final long START = 1_700_000_000_000L;

    private OtlpHttpTraceExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new OtlpHttpTraceExporter();
    }

    // ==================== 测试替身 ====================

    private static ExportedSpan span(String module, String action, String parentSpanId) {
        return new ExportedSpan("trace-1", "0102030405060708", parentSpanId, "cangjie-agent",
                module, action, "success", "ok", START, 42L, null, null, null);
    }

    private static ExportedSpan spanWith(Map<String, Object> metadata) {
        return new ExportedSpan("trace-1", "0102030405060708", "trace-1", "cangjie-agent",
                "chat", "chat", "success", "ok", START, 42L, "{\"query\":\"q\"}", "{\"answer\":\"a\"}", metadata);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** 取指定 span 节点里的某个 attribute 的值部分 */
    private static Object attributeValue(Map<String, Object> spanNode, String key) {
        for (Map<String, Object> attribute : castList(spanNode.get("attributes"))) {
            if (key.equals(attribute.get("key"))) {
                return castMap(attribute.get("value"));
            }
        }
        return null;
    }

    private static Map<String, Object> firstSpanNode(String payload) {
        List<Map<String, Object>> resourceSpans = castList(JsonUtils.parseMap(payload).get("resourceSpans"));
        List<Map<String, Object>> scopeSpans = castList(resourceSpans.get(0).get("scopeSpans"));
        return castList(scopeSpans.get(0).get("spans")).get(0);
    }

    // ==================== OTLP 结构 ====================

    @Nested
    class Payload {

        @Test
        void shouldWrapSpansIntoResourceAndScope() {
            Map<String, Object> root = JsonUtils.parseMap(exporter.toPayload(List.of(span("chat", "send", null))));

            List<Map<String, Object>> resourceSpans = castList(root.get("resourceSpans"));
            assertThat(resourceSpans).hasSize(1);
            assertThat(attributeValue(castMap(resourceSpans.get(0).get("resource")), "service.name"))
                    .isEqualTo(Map.of("stringValue", "cangjie-agent"));

            List<Map<String, Object>> scopeSpans = castList(resourceSpans.get(0).get("scopeSpans"));
            assertThat(scopeSpans).hasSize(1);
            assertThat(castMap(scopeSpans.get(0).get("scope"))).containsEntry("name", "cangjie-agent");
            assertThat(castList(scopeSpans.get(0).get("spans"))).hasSize(1);
        }

        @Test
        void shouldSplitResourceSpansByServiceName() {
            // 混在一个 resource 里发，对端会把两个服务的 span 认成同一个
            ExportedSpan other = new ExportedSpan("trace-2", "0102030405060709", null, "cangjie-worker",
                    "workflow", "run", "success", "ok", START, 10L, null, null, null);

            List<Map<String, Object>> resourceSpans = castList(
                    JsonUtils.parseMap(exporter.toPayload(List.of(span("chat", "send", null), other)))
                            .get("resourceSpans"));

            assertThat(resourceSpans).hasSize(2);
            assertThat(resourceSpans)
                    .allSatisfy(resource -> assertThat(castList(resource.get("scopeSpans"))).hasSize(1));
            assertThat(castList(castList(resourceSpans.get(0).get("scopeSpans")).get(0).get("spans"))).hasSize(1);
        }

        @Test
        void shouldDefaultServiceNameWhenMissing() {
            ExportedSpan blank = new ExportedSpan("trace-3", "010203040506070a", null, null,
                    "chat", "send", "success", "ok", START, 1L, null, null, null);

            Map<String, Object> resource = castMap(castList(
                    JsonUtils.parseMap(exporter.toPayload(List.of(blank))).get("resourceSpans")).get(0)
                    .get("resource"));

            assertThat(attributeValue(resource, "service.name"))
                    .isEqualTo(Map.of("stringValue", "cangjie-agent"));
        }

        @Test
        void shouldRenderSpanNodeWithHexIdKindAndNanoTime() {
            Map<String, Object> node = firstSpanNode(
                    exporter.toPayload(List.of(span("retrieval", "search", "a1b2c3d4e5f60718"))));

            assertThat(String.valueOf(node.get("traceId"))).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(node.get("spanId")).isEqualTo("0102030405060708");
            assertThat(node.get("parentSpanId")).isEqualTo("a1b2c3d4e5f60718");
            assertThat(node.get("name")).isEqualTo("retrieval.search");
            // 有父且是检索：客户端 span，对端才会把它放进依赖视图
            assertThat(node.get("kind")).isEqualTo(3);
            assertThat(node.get("startTimeUnixNano")).isEqualTo(String.valueOf(START * 1_000_000L));
            assertThat(node.get("endTimeUnixNano")).isEqualTo(String.valueOf((START + 42L) * 1_000_000L));
            assertThat(castMap(node.get("status"))).containsEntry("code", 1).containsEntry("message", "ok");
        }

        @Test
        void shouldMarkEntrySpanAsServerAndOmitParent() {
            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(span("chat", "send", null))));

            assertThat(node).doesNotContainKey("parentSpanId");
            assertThat(node.get("kind")).isEqualTo(2);
        }

        @Test
        void shouldRenderFailureWithErrorCodeAndEmptyMessage() {
            ExportedSpan failed = new ExportedSpan("trace-1", "0102030405060708", "trace-1", "cangjie-agent",
                    "llm", "complete", "fail", null, START, 0L, null, null, null);

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(failed)));

            Map<String, Object> status = castMap(node.get("status"));
            assertThat(status).containsEntry("code", 2).containsEntry("message", "");
            // 耗时为 0 也要有结束时间，否则对端算不出持续时长
            assertThat(node.get("endTimeUnixNano")).isEqualTo(node.get("startTimeUnixNano"));
            assertThat(attributeValue(node, "cangjie.status")).isEqualTo(Map.of("stringValue", "fail"));
        }

        @Test
        void shouldFallbackNameWhenModuleAndActionMissing() {
            ExportedSpan bare = new ExportedSpan("trace-1", "0102030405060708", "trace-1", "cangjie-agent",
                    null, null, "success", "ok", START, 1L, null, null, null);

            assertThat(firstSpanNode(exporter.toPayload(List.of(bare))).get("name")).isEqualTo("unknown.call");
        }
    }

    // ==================== gen_ai 语义投影 ====================

    @Nested
    class Attributes {

        @Test
        void shouldMapModuleToOperationName() {
            assertThat(operation("retrieval", "search")).isEqualTo("information_retrieval");
            assertThat(operation("knowledge", "retrieve")).isEqualTo("information_retrieval");
            assertThat(operation("tool", "invoke")).isEqualTo("execute_tool");
            assertThat(operation("workflow", "run")).isEqualTo("invoke_agent");
            assertThat(operation("agent", "chat")).isEqualTo("invoke_agent");
            // 模型调用才是 chat，整轮对话（send）不是
            assertThat(operation("chat", "chat")).isEqualTo("chat");
            assertThat(operation("llm", "complete")).isEqualTo("chat");
            assertThat(operation("model", "generate")).isEqualTo("chat");
            assertThat(operation("chat", "send")).isNull();
            assertThat(operation("order", "create")).isNull();
        }

        private String operation(String module, String action) {
            Object value = attributeValue(firstSpanNode(
                    exporter.toPayload(List.of(span(module, action, "trace-1")))), "gen_ai.operation.name");
            return value == null ? null : String.valueOf(castMap(value).get("stringValue"));
        }

        @Test
        void shouldProjectMetadataOntoGenAiKeys() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("model", "qwen-max");
            metadata.put("provider", "dashscope");
            metadata.put("inputTokens", 120);
            metadata.put("outputTokens", 30);
            metadata.put("totalTokens", 150);
            metadata.put("finishReason", "stop");
            metadata.put("hitCount", 2);
            metadata.put("already.qualified", "keep");

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(spanWith(metadata))));

            assertThat(attributeValue(node, "gen_ai.request.model")).isEqualTo(Map.of("stringValue", "qwen-max"));
            assertThat(attributeValue(node, "gen_ai.provider.name")).isEqualTo(Map.of("stringValue", "dashscope"));
            assertThat(attributeValue(node, "gen_ai.usage.input_tokens")).isEqualTo(Map.of("intValue", "120"));
            assertThat(attributeValue(node, "gen_ai.usage.output_tokens")).isEqualTo(Map.of("intValue", "30"));
            assertThat(attributeValue(node, "gen_ai.usage.total_tokens")).isEqualTo(Map.of("intValue", "150"));
            assertThat(attributeValue(node, "gen_ai.response.finish_reasons")).isEqualTo(Map.of("stringValue", "stop"));
            // 自家维度不能顶掉别人的命名空间，统一加前缀；已带点号的原样透出
            assertThat(attributeValue(node, "cangjie.hitCount")).isEqualTo(Map.of("intValue", "2"));
            assertThat(attributeValue(node, "already.qualified")).isEqualTo(Map.of("stringValue", "keep"));
        }

        @Test
        void shouldSkipInputOutputKeysInsideMetadata() {
            // 正文只走 include-payload 闸门，藏在 metadata 里就等于绕开了它
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("input", "{\"secret\":1}");
            metadata.put("output", "{\"secret\":2}");
            metadata.put("kbId", "kb-1");

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(spanWith(metadata))));

            assertThat(node.get("attributes")).asString().doesNotContain("secret");
            assertThat(attributeValue(node, "cangjie.kbId")).isEqualTo(Map.of("stringValue", "kb-1"));
        }

        @Test
        void shouldConvertValueTypesNatively() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("rerank", Boolean.TRUE);
            metadata.put("score", 0.91);
            metadata.put("docIds", List.of("d1", "d2"));
            metadata.put("nothing", null);

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(spanWith(metadata))));

            // 数字与布尔走原生类型，看板才能聚合；嵌套结构只能摊成文本
            assertThat(attributeValue(node, "cangjie.rerank")).isEqualTo(Map.of("boolValue", true));
            assertThat(attributeValue(node, "cangjie.score")).isEqualTo(Map.of("doubleValue", 0.91));
            assertThat(attributeValue(node, "cangjie.docIds")).isEqualTo(Map.of("stringValue", "[\"d1\",\"d2\"]"));
            assertThat(attributeValue(node, "cangjie.nothing")).isEqualTo(Map.of("stringValue", ""));
        }

        @Test
        void shouldKeepPayloadPrivateByDefault() {
            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(spanWith(Map.of()))));

            assertThat(attributeValue(node, "gen_ai.prompt")).isNull();
            assertThat(attributeValue(node, "gen_ai.completion")).isNull();
        }

        @Test
        void shouldExportPayloadOnlyWhenExplicitlyEnabled() {
            ReflectionTestUtils.setField(exporter, "includePayload", true);

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(spanWith(Map.of()))));

            assertThat(attributeValue(node, "gen_ai.prompt")).isEqualTo(Map.of("stringValue", "{\"query\":\"q\"}"));
            assertThat(attributeValue(node, "gen_ai.completion")).isEqualTo(Map.of("stringValue", "{\"answer\":\"a\"}"));
        }

        @Test
        void shouldOmitBlankPayloadEvenWhenEnabled() {
            ReflectionTestUtils.setField(exporter, "includePayload", true);

            Map<String, Object> node = firstSpanNode(exporter.toPayload(List.of(span("chat", "send", "trace-1"))));

            assertThat(attributeValue(node, "gen_ai.prompt")).isNull();
            assertThat(attributeValue(node, "gen_ai.completion")).isNull();
        }
    }

    // ==================== ID 规范化 ====================

    @Nested
    class IdNormalization {

        @Test
        void shouldKeepWellFormedHexAsIs() {
            String hex = "0123456789abcdef0123456789abcdef";

            assertThat(OtlpHttpTraceExporter.traceIdHex(hex)).isEqualTo(hex);
            // 带横线的 UUID 与大写都能归一
            assertThat(OtlpHttpTraceExporter.traceIdHex("01234567-89AB-CDEF-0123-456789ABCDEF")).isEqualTo(hex);
            assertThat(OtlpHttpTraceExporter.spanIdHex("0102030405060708")).isEqualTo("0102030405060708");
        }

        @Test
        void shouldPadLeftWhenTooShort() {
            assertThat(OtlpHttpTraceExporter.spanIdHex("abcdef")).isEqualTo("0000000000abcdef");
            assertThat(OtlpHttpTraceExporter.traceIdHex("abc")).hasSize(32).endsWith("abc");
        }

        @Test
        void shouldHashArbitraryIdDeterministically() {
            String first = OtlpHttpTraceExporter.traceIdHex("trace-2026-1012-0001");
            String second = OtlpHttpTraceExporter.traceIdHex("trace-2026-1012-0001");

            // 同一条链路各处算出的值必须一致，否则对端拼不回一条 trace
            assertThat(first).hasSize(32).isEqualTo(second)
                    .isNotEqualTo(OtlpHttpTraceExporter.traceIdHex("trace-2026-1012-0002"));
            assertThat(OtlpHttpTraceExporter.spanIdHex("随便写的 span")).hasSize(16).matches("[0-9a-f]{16}");
        }

        @Test
        void shouldReplaceAllZeroId() {
            // 全零在 OTLP 里是「无效 ID」，原样发出去对端会整条丢掉
            assertThat(OtlpHttpTraceExporter.traceIdHex("0".repeat(32))).hasSize(32).isNotEqualTo("0".repeat(32));
            assertThat(OtlpHttpTraceExporter.spanIdHex("0000000000000000")).hasSize(16).isNotEqualTo("0".repeat(16));
        }

        @Test
        void shouldSurviveNullId() {
            assertThat(OtlpHttpTraceExporter.traceIdHex(null)).hasSize(32).matches("[0-9a-f]{32}");
            assertThat(OtlpHttpTraceExporter.spanIdHex(null)).hasSize(16).matches("[0-9a-f]{16}");
        }
    }

    // ==================== 配置解析与装配 ====================

    /** 只给地址把定时器拨到无限远：不让后台线程抢在断言前把队列抽干 */
    private void startWith(String url) {
        ReflectionTestUtils.setField(exporter, "endpoint", url);
        ReflectionTestUtils.setField(exporter, "flushIntervalMs", Long.MAX_VALUE);
        exporter.init();
    }

    @Nested
    class Wiring {

        @Test
        void shouldParseHeadersAndIgnoreMalformedPairs() {
            assertThat(OtlpHttpTraceExporter.parseHeaders("authorization=Bearer x-token , x-tenant=acme"))
                    .containsExactly(Map.entry("authorization", "Bearer x-token"), Map.entry("x-tenant", "acme"));
            assertThat(OtlpHttpTraceExporter.parseHeaders("nope,=,k=v")).containsOnlyKeys("k");
            assertThat(OtlpHttpTraceExporter.parseHeaders(" ")).isEmpty();
        }

        @Test
        void shouldStaySilentWithoutEndpoint() {
            // 开了开关却没给地址：装配阶段就停下，别让每条 span 白排一次队
            exporter.init();
            try {
                exporter.export(span("chat", "send", null));

                assertThat(exporter.queuedSize()).isZero();
                assertThat(exporter.droppedCount()).isZero();
            } finally {
                exporter.destroy();
            }
        }

        @Test
        void shouldAppendTracesPathToBareHost() {
            startWith("http://127.0.0.1:4318");
            try {
                assertThat((java.net.URI) ReflectionTestUtils.getField(exporter, "target"))
                        .hasToString("http://127.0.0.1:4318/v1/traces");
            } finally {
                exporter.destroy();
            }
        }

        @Test
        void shouldNotConfigureTwiceOnFullUrl() {
            startWith("http://127.0.0.1:4318/v1/traces");
            try {
                assertThat((java.net.URI) ReflectionTestUtils.getField(exporter, "target"))
                        .hasToString("http://127.0.0.1:4318/v1/traces");
            } finally {
                exporter.destroy();
            }
        }

        @Test
        void shouldDropNewSpanWhenBufferFull() {
            ReflectionTestUtils.setField(exporter, "endpoint", "http://127.0.0.1:4318");
            ReflectionTestUtils.setField(exporter, "queueCapacity", 1);
            ReflectionTestUtils.setField(exporter, "flushIntervalMs", Long.MAX_VALUE);
            exporter.init();
            try {
                exporter.export(span("chat", "send", null));
                exporter.export(span("chat", "send", null));

                assertThat(exporter.queuedSize()).isEqualTo(1);
                // 对端跟不上时丢出口数据，不丢业务：本地库里已经落了一份
                assertThat(exporter.droppedCount()).isEqualTo(1);
            } finally {
                exporter.destroy();
            }
        }

        @Test
        void shouldIgnoreNullSpan() {
            startWith("http://127.0.0.1:4318");
            try {
                exporter.export(null);

                assertThat(exporter.queuedSize()).isZero();
            } finally {
                exporter.destroy();
            }
        }

        @Test
        void shouldDestroyWithoutInit() {
            assertThatCode(() -> exporter.destroy()).doesNotThrowAnyException();
        }
    }

    // ==================== 发送 ====================

    @Nested
    class Transport {

        private HttpServer server;
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();
        private final AtomicReference<String> tenant = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();
        private final AtomicInteger status = new AtomicInteger(200);
        private final AtomicInteger hits = new AtomicInteger();

        @BeforeEach
        void startServer() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                hits.incrementAndGet();
                path.set(exchange.getRequestURI().getPath());
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                tenant.set(exchange.getRequestHeaders().getFirst("x-tenant"));
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
        }

        @AfterEach
        void stopServer() {
            exporter.destroy();
            server.stop(0);
        }

        private void startExporter(int batch) {
            ReflectionTestUtils.setField(exporter, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(exporter, "headersRaw", "x-tenant=acme");
            ReflectionTestUtils.setField(exporter, "timeoutMs", 5000);
            ReflectionTestUtils.setField(exporter, "queueCapacity", 100);
            ReflectionTestUtils.setField(exporter, "batchSize", batch);
            ReflectionTestUtils.setField(exporter, "flushIntervalMs", Long.MAX_VALUE);
            exporter.init();
        }

        @Test
        void shouldPostBatchToCollectorEndpoint() {
            startExporter(200);

            exporter.export(span("chat", "send", null));
            exporter.export(span("retrieval", "search", "trace-1"));
            ReflectionTestUtils.invokeMethod(exporter, "flushQuietly");

            assertThat(hits).hasValue(1);
            assertThat(path).hasValue("/v1/traces");
            assertThat(contentType).hasValue("application/json");
            // 配置的附加头部要真的带上，否则对端鉴权直接 401
            assertThat(tenant).hasValue("acme");
            assertThat(castList(JsonUtils.parseMap(body.get()).get("resourceSpans"))).hasSize(1);
            assertThat(exporter.queuedSize()).isZero();
        }

        @Test
        void shouldStopDrainingWhenCollectorRejectsBatch() {
            status.set(503);
            startExporter(1);

            exporter.export(span("chat", "send", null));
            exporter.export(span("retrieval", "search", "trace-1"));
            ReflectionTestUtils.invokeMethod(exporter, "flushQuietly");

            // 对端不通时接着抖只会把超时堆成一串，剩下的等下一轮
            assertThat(hits).hasValue(1);
            assertThat(exporter.sendFailureCount()).isEqualTo(1);
            assertThat(exporter.queuedSize()).isEqualTo(1);
        }

        @Test
        void shouldCountFailureWhenCollectorUnreachable() {
            server.stop(0);
            startExporter(200);

            exporter.export(span("chat", "send", null));
            ReflectionTestUtils.invokeMethod(exporter, "flushQuietly");

            assertThat(exporter.sendFailureCount()).isEqualTo(1);
            assertThat(exporter.queuedSize()).isZero();
        }
    }
}
