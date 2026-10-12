package cn.cangjiecloud.observability.exporter;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.observability.ExportedSpan;
import cn.cangjiecloud.core.observability.TraceExporter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OTLP/HTTP span 出口：把本地链路推到 Collector / Jaeger / Grafana Tempo 等已有观测体系。
 * <p>
 * 手写 OTLP JSON 而不引 opentelemetry-sdk：本仓库离线构建，OTLP SDK 及其一堆传递依赖
 * （grpc / api / semconv）并不在本地仓库里，为一个出口拖进整个 SDK 不划算；
 * OTLP/HTTP 的 JSON 编码就是 protobuf 的字段名映射，够用且能对着 spec 校验。
 * <p>
 * 属性名按 OTel GenAI 语义约定（{@code gen_ai.*}）投影，这样第三方看板能直接按模型、
 * token、操作类型切片，而不是只看到一串我们自己发明的 module/action。
 * <p>
 * 出口只入队、由后台线程批量 POST：链路写入点在每个业务请求上，对端抖动绝不能回压到对话。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cangjie.observability.exporter.otlp.enabled", havingValue = "true")
public class OtlpHttpTraceExporter implements TraceExporter {

    /** OTLP SpanKind：1 内部、2 服务端、3 客户端 */
    private static final int KIND_INTERNAL = 1;
    private static final int KIND_SERVER = 2;
    private static final int KIND_CLIENT = 3;

    /** OTLP StatusCode：1 成功、2 失败（0 是未设置，看板会当灰处理） */
    private static final int CODE_OK = 1;
    private static final int CODE_ERROR = 2;

    private static final String TRACES_PATH = "/v1/traces";

    @Value("${cangjie.observability.exporter.otlp.endpoint:}")
    private String endpoint;

    /** 附加请求头，形如 {@code authorization=Bearer xxx,x-tenant=acme} */
    @Value("${cangjie.observability.exporter.otlp.headers:}")
    private String headersRaw;

    @Value("${cangjie.observability.exporter.otlp.timeout-ms:3000}")
    private int timeoutMs;

    /**
     * 是否把输入 / 输出正文一起外发。
     * <p>
     * 默认关：query 与知识库正文是要出内网的敏感数据，开了出口就等于把内容送到第三方，
     * 这个决定必须由部署方显式做出，不能跟着「开启链路导出」顺手带上。
     */
    @Value("${cangjie.observability.exporter.otlp.include-payload:false}")
    private boolean includePayload;

    @Value("${cangjie.observability.exporter.otlp.queue-capacity:5000}")
    private int queueCapacity;

    @Value("${cangjie.observability.exporter.otlp.batch-size:200}")
    private int batchSize;

    @Value("${cangjie.observability.exporter.otlp.flush-interval-ms:5000}")
    private long flushIntervalMs;

    private BlockingQueue<ExportedSpan> queue;
    private ScheduledExecutorService flusher;
    private HttpClient httpClient;
    private Map<String, String> headers = Map.of();
    private URI target;

    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong sendFailures = new AtomicLong();
    private volatile long lastReportedDropped;

    @Override
    public String name() {
        return "otlp-http";
    }

    @PostConstruct
    void init() {
        queue = new LinkedBlockingQueue<>(Math.max(1, queueCapacity));
        headers = parseHeaders(headersRaw);
        if (!StringUtils.hasText(endpoint)) {
            // 开了开关却没给地址：与其静默丢掉全部出口数据，不如把这一步明确停在装配阶段
            log.error("otlp 出口已启用但未配置 endpoint，本次不启动导出（cangjie.observability.exporter.otlp.endpoint）");
            return;
        }
        target = normalizeTarget(endpoint.trim());
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200, timeoutMs)))
                .build();
        long interval = Math.max(500L, flushIntervalMs);
        flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "otlp-trace-exporter");
            thread.setDaemon(true);
            return thread;
        });
        flusher.scheduleWithFixedDelay(this::flushQuietly, interval, interval, TimeUnit.MILLISECONDS);
        log.info("otlp 出口已启用: target={}, includePayload={}", target, includePayload);
    }

    @PreDestroy
    void destroy() {
        if (flusher != null) {
            flusher.shutdown();
        }
        flushQuietly();
    }

    @Override
    public void export(ExportedSpan span) {
        if (span == null || httpClient == null) {
            return;
        }
        if (!queue.offer(span)) {
            // 对端跟不上时丢出口数据，不丢业务：链路已经在本地库里落了一份
            dropped.incrementAndGet();
        }
    }

    private void flushQuietly() {
        if (httpClient == null || queue == null) {
            return;
        }
        while (!queue.isEmpty()) {
            List<ExportedSpan> batch = new ArrayList<>(Math.max(1, batchSize));
            queue.drainTo(batch, Math.max(1, batchSize));
            if (batch.isEmpty()) {
                break;
            }
            if (!send(toPayload(batch))) {
                // 对端不通时接着抖只会把超时堆成一串，剩下的等下一轮
                break;
            }
        }
        reportDropped();
    }

    /** 一批一请求；返回 false 表示这批没送出去 */
    private boolean send(String payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofMillis(Math.max(200, timeoutMs)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                sendFailures.incrementAndGet();
                log.warn("otlp 出口返回 {}: {}", response.statusCode(), truncate(response.body()));
                return false;
            }
            return true;
        } catch (Exception ex) {
            sendFailures.incrementAndGet();
            log.warn("otlp 出口发送失败（本批已丢弃）: {}", ex.toString());
            return false;
        }
    }

    private void reportDropped() {
        long total = dropped.get();
        if (total > lastReportedDropped) {
            log.warn("otlp 出口缓冲已满，累计丢弃 {} 条（本轮新增 {} 条），"
                    + "持续出现请调大 otlp.queue-capacity 或缩短 flush-interval-ms",
                    total, total - lastReportedDropped);
            lastReportedDropped = total;
        }
    }

    // ==================== OTLP JSON 投影 ====================

    /**
     * 按服务名分组组装 resourceSpans：OTLP 的资源属性是「一批 span 共用一个 resource」，
     * 混在一起发会让对端把不同服务的 span 认成同一个。
     */
    String toPayload(List<ExportedSpan> spans) {
        Map<String, List<Map<String, Object>>> byResource = new LinkedHashMap<>();
        for (ExportedSpan span : spans) {
            String resource = span.serviceName() == null ? "cangjie-agent" : span.serviceName();
            byResource.computeIfAbsent(resource, key -> new ArrayList<>()).add(toSpanNode(span));
        }
        List<Object> resourceSpans = new ArrayList<>(byResource.size());
        byResource.forEach((resource, spanNodes) -> resourceSpans.add(Map.of(
                "resource", Map.of("attributes", List.of(
                        stringAttribute("service.name", resource))),
                "scopeSpans", List.of(Map.of(
                        "scope", Map.of("name", "cangjie-agent"),
                        "spans", spanNodes)))));
        return JsonUtils.toJSONString(Map.of("resourceSpans", resourceSpans));
    }

    private Map<String, Object> toSpanNode(ExportedSpan span) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("traceId", traceIdHex(span.traceId()));
        node.put("spanId", spanIdHex(span.spanId()));
        if (StringUtils.hasText(span.parentSpanId())) {
            node.put("parentSpanId", spanIdHex(span.parentSpanId()));
        }
        node.put("name", spanName(span));
        node.put("kind", kind(span));
        node.put("startTimeUnixNano", String.valueOf(span.startEpochMs() * 1_000_000L));
        // 耗时为 0 的 span 也要有结束时间，否则对端算不出持续时长
        node.put("endTimeUnixNano", String.valueOf(Math.max(span.endEpochMs(), span.startEpochMs()) * 1_000_000L));
        node.put("attributes", attributes(span));
        node.put("status", Map.of(
                "code", span.failed() ? CODE_ERROR : CODE_OK,
                "message", span.message() == null ? "" : span.message()));
        return node;
    }

    /** 名字带上模块与动作，对端列表页一眼能认出是哪一步 */
    private static String spanName(ExportedSpan span) {
        String module = span.module() == null ? "unknown" : span.module();
        String action = span.action() == null ? "call" : span.action();
        return module + "." + action;
    }

    private static int kind(ExportedSpan span) {
        if (!StringUtils.hasText(span.parentSpanId())) {
            return KIND_SERVER;
        }
        String module = span.module() == null ? "" : span.module();
        // 检索与模型调用是「打出去的请求」，标成 CLIENT 对端才会把它们放进依赖视图
        return switch (module) {
            case "retrieval", "knowledge", "llm", "model", "tool" -> KIND_CLIENT;
            default -> KIND_INTERNAL;
        };
    }

    /**
     * 语义约定投影：{@code gen_ai.*} 给模型与检索维度，其余维度原样带过去。
     * <p>
     * 只发 module/action 的话，对端看板要自己重新发明一套含义；而 gen_ai 是 OTel 已定名，
     * 主流的 LLM 观测面板开箱就能按模型和 token 切片。
     */
    List<Map<String, Object>> attributes(ExportedSpan span) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(stringAttribute("cangjie.module", span.module()));
        attributes.add(stringAttribute("cangjie.action", span.action()));
        attributes.add(stringAttribute("cangjie.status", span.status()));

        String operation = operationName(span);
        if (operation != null) {
            attributes.add(stringAttribute("gen_ai.operation.name", operation));
        }

        Map<String, Object> metadata = span.metadata();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (!"input".equals(key) && !"output".equals(key)) {
                    attributes.add(attribute(genAiKey(key), value));
                }
            });
        }

        if (includePayload) {
            if (StringUtils.hasText(span.input())) {
                attributes.add(stringAttribute("gen_ai.prompt", span.input()));
            }
            if (StringUtils.hasText(span.output())) {
                attributes.add(stringAttribute("gen_ai.completion", span.output()));
            }
        }
        return attributes;
    }

    private static String operationName(ExportedSpan span) {
        String module = span.module() == null ? "" : span.module();
        String action = span.action() == null ? "" : span.action();
        return switch (module) {
            case "retrieval", "knowledge" -> "information_retrieval";
            case "tool" -> "execute_tool";
            case "workflow", "agent", "harness" -> "invoke_agent";
            // 对话主链路里既有入口 span 也有模型调用，靠动作区分：send 是整轮对话，chat/complete 才是模型请求
            case "chat", "llm", "model" -> action.matches("chat|complete|generate") ? "chat" : null;
            default -> null;
        };
    }

    /** metadata 里已经带着模型与 token 维度，翻成 gen_ai 的标准名对端才认 */
    private static String genAiKey(String key) {
        return switch (key) {
            case "model", "modelName", "modelId" -> "gen_ai.request.model";
            case "provider", "providerType", "platform" -> "gen_ai.provider.name";
            case "inputTokens" -> "gen_ai.usage.input_tokens";
            case "outputTokens" -> "gen_ai.usage.output_tokens";
            case "totalTokens", "tokens" -> "gen_ai.usage.total_tokens";
            case "finishReason" -> "gen_ai.response.finish_reasons";
            default -> key.contains(".") ? key : "cangjie." + key;
        };
    }

    private static Map<String, Object> stringAttribute(String key, String value) {
        return Map.of("key", key, "value", Map.of("stringValue", value == null ? "" : value));
    }

    /**
     * 值类型映射：数字与布尔走 OTLP 的原生类型（看板要聚合与画直方图），
     * 其余一律转字符串，嵌套对象/数组转 JSON 文本 —— OTLP 的 attribute 不接受嵌套结构。
     */
    private static Map<String, Object> attribute(String key, Object value) {
        if (value instanceof Boolean bool) {
            return Map.of("key", key, "value", Map.of("boolValue", bool));
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return Map.of("key", key, "value", Map.of("intValue", String.valueOf(value)));
        }
        if (value instanceof Number number) {
            return Map.of("key", key, "value", Map.of("doubleValue", number.doubleValue()));
        }
        if (value == null) {
            return stringAttribute(key, "");
        }
        if (value instanceof CharSequence text) {
            return stringAttribute(key, text.toString());
        }
        return stringAttribute(key, JsonUtils.toJSONString(value));
    }

    // ==================== ID 规范化 ====================

    /**
     * traceId 规范化成 16 字节（32 个 hex 字符）。
     * <p>
     * 我们的 traceId 是业务侧生成的任意串，OTLP 却要求定长 hex；不匹配时取 SHA-256 前 32 位：
     * 同一条链路各处算出的值一致，对端仍能把 span 拼成一条 trace。
     */
    static String traceIdHex(String raw) {
        return normalizeHex(raw, 32);
    }

    static String spanIdHex(String raw) {
        return normalizeHex(raw, 16);
    }

    private static String normalizeHex(String raw, int length) {
        String candidate = raw == null ? "" : raw.replace("-", "").toLowerCase();
        if (isHex(candidate)) {
            if (candidate.length() == length) {
                // 全零在 OTLP 里是「无效 ID」，换成哈希值而不是原样发出去
                return isAllZero(candidate) ? hashHex(raw, length) : candidate;
            }
            if (candidate.length() < length) {
                return padLeft(candidate, length);
            }
        }
        return hashHex(raw, length);
    }

    private static boolean isHex(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static String padLeft(String text, int length) {
        return "0".repeat(length - text.length()) + text;
    }

    private static String hashHex(String raw, int length) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(raw).getBytes(StandardCharsets.UTF_8));
            String truncated = HexFormat.of().formatHex(digest).substring(0, length);
            // 全零在 OTLP 里是无效 ID，哈希真撞上全零就把最后一位改掉
            return isAllZero(truncated) ? truncated.substring(0, length - 1) + "1" : truncated;
        } catch (Exception ex) {
            return padLeft("1", length);
        }
    }

    // ==================== 配置解析 ====================

    /** 补上 OTLP 的接口路径：给到 {@code http://host:4318} 也能用 */
    private static URI normalizeTarget(String configured) {
        String url = configured.endsWith(TRACES_PATH) ? configured : configured + TRACES_PATH;
        return URI.create(url);
    }

    static Map<String, String> parseHeaders(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : raw.split(",")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                parsed.put(pair.substring(0, index).trim(), pair.substring(index + 1).trim());
            }
        }
        return parsed;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    /** 缓冲中的待发送条数（供单测与监控） */
    int queuedSize() {
        return queue == null ? 0 : queue.size();
    }

    long droppedCount() {
        return dropped.get();
    }

    long sendFailureCount() {
        return sendFailures.get();
    }
}
