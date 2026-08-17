package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 节点 — 发送 HTTP 请求
 * <p>
 * config 参数：
 * - method: HTTP 方法（GET/POST/PUT/DELETE，默认 GET）
 * - url: 请求 URL（支持 {variable} 占位符）
 * - body: 请求体 JSON（支持 {variable} 占位符）
 * - headers: 请求头 Map（可选）
 */
@Slf4j
@Component
public class ApiNode implements WorkflowNode {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getType() {
        return "api";
    }

    @Override
    public String getName() {
        return "API";
    }

    @Override
    public String getDescription() {
        return "发送 HTTP 请求调用外部 API";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        String method = ((String) safeConfig.getOrDefault("method", "GET")).toUpperCase();
        String url = resolveVariables((String) safeConfig.get("url"), safeInputs);
        String body = resolveVariables((String) safeConfig.get("body"), safeInputs);

        if (url == null || url.isEmpty()) {
            return Map.of("api_success", false, "api_error", "URL 不能为空");
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60));

            switch (method) {
                case "POST" -> reqBuilder.POST(body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody());
                case "PUT" -> reqBuilder.PUT(body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> reqBuilder.DELETE();
                default -> reqBuilder.GET();
            }

            // 默认 JSON content-type
            if ("POST".equals(method) || "PUT".equals(method)) {
                reqBuilder.header("Content-Type", "application/json");
            }

            // 自定义 headers
            @SuppressWarnings("unchecked")
            Map<String, String> headers = safeConfig.containsKey("headers") && safeConfig.get("headers") instanceof Map
                    ? (Map<String, String>) safeConfig.get("headers")
                    : Map.of();
            headers.forEach(reqBuilder::header);

            HttpResponse<String> response = HTTP_CLIENT.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            log.info("ApiNode: {} {} → {}", method, url, response.statusCode());

            Map<String, Object> output = new HashMap<>();
            output.put("api_status_code", response.statusCode());
            output.put("api_output", response.body());
            output.put("api_success", response.statusCode() >= 200 && response.statusCode() < 300);
            return output;

        } catch (Exception e) {
            log.error("ApiNode: 请求失败 {} {}", method, url, e);
            return Map.of("api_success", false, "api_error", e.getMessage());
        }
    }

    private String resolveVariables(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
