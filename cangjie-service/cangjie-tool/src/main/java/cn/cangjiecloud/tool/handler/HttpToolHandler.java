package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.executor.HttpRequestExecutor;
import cn.cangjiecloud.tool.util.ToolNaming;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 工具处理器
 */
@Slf4j
@Component
@ToolHandlerType(ToolConstants.ToolType.HTTP)
public class HttpToolHandler extends AbsToolHandler {

    @Override
    public ToolSpecification buildToolSpecification(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(ToolNaming.buildToolName(entity.getId()))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(ToolConstants.ToolType.HTTP)
                .parameters(parseParameters(entity.getParameters()))
                .build();
    }

    @Override
    public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> config = parseConfig(entity.getConfig());
            String url = (String) config.getOrDefault("url", "");
            String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) config.get("headers");

            String bodyTemplate = (String) config.getOrDefault("body", "");
            boolean hasBodyTemplate = bodyTemplate != null && !bodyTemplate.isEmpty();

            // body 中写了 {{param}} 的参数直接替换进 body，没写的参数自动拼到 query
            String bodyStr = resolvePlaceholders(bodyTemplate, params);
            Map<String, String> queryParams = buildQueryParams(params, bodyTemplate);
            String urlStr = resolvePlaceholders(url, params);

            // 如果没配 body 模板且是 POST/PUT/PATCH，参数整体序列化为 JSON body
            if (!hasBodyTemplate && !"GET".equals(method) && !"DELETE".equals(method)) {
                bodyStr = com.alibaba.fastjson.JSON.toJSONString(params);
                queryParams = null;
            }

            String result = new HttpRequestExecutor().execute(urlStr, method, headers, bodyStr, queryParams);
            long cost = System.currentTimeMillis() - start;
            log.info("HTTP 工具执行成功: {} ({}), 耗时 {}ms", entity.getName(), entity.getId(), cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("HTTP 工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

    private Map<String, String> buildQueryParams(Map<String, Object> params, String bodyTemplate) {
        Map<String, String> result = new HashMap<>();
        if (params == null) return result;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            // 如果这个参数已经在 body 模板里通过 {{param}} 引用了，就不再拼到 query
            if (bodyTemplate != null && bodyTemplate.contains("{{" + entry.getKey() + "}}")) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        try {
            return new com.alibaba.fastjson2.JSONObject(com.alibaba.fastjson.JSON.parseObject(parameters));
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isEmpty()) return Map.of();
        try {
            return com.alibaba.fastjson.JSON.parseObject(config);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolvePlaceholders(String template, Map<String, Object> params) {
        if (template == null || params == null) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        // 清理未被替换的占位符整段参数（如 ?all={{all}}& → 移除，而不是残留 all=）
        result = result.replaceAll("[?&][^=?&]+=\\{\\{[^}]+}}", "");
        // 如果第一个参数被移除导致 path&key=val，将首个 & 修正为 ?
        if (result.contains("&") && !result.contains("?")) {
            result = result.replaceFirst("&", "?");
        }
        // 清理 body 或 URL 路径中残留的裸占位符
        result = result.replaceAll("\\{\\{[^}]+}}", "");
        return result;
    }
}