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
            String method = (String) config.getOrDefault("method", "GET");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) config.get("headers");

            // 将 params 中的参数填充占位符
            String body = (String) config.getOrDefault("body", "");
            String urlStr = resolvePlaceholders(url, params);
            String bodyStr = resolvePlaceholders(body, params);

            String result = new HttpRequestExecutor().execute(urlStr, method, headers, bodyStr, null);
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
        return result;
    }
}