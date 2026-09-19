package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.executor.McpClientExecutor;
import cn.cangjiecloud.tool.mcp.McpClientManager;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 协议工具处理器
 */
@Slf4j
@Component
@ToolHandlerType(ToolConstants.ToolType.MCP)
public class McpToolHandler extends AbsToolHandler {

    private final McpClientManager mcpClientManager;

    public McpToolHandler(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    @Override
    public ToolSpecification buildToolSpecification(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(specName(entity))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(ToolConstants.ToolType.MCP)
                .parameters(parseParameters(entity.getParameters()))
                .build();
    }

    @Override
    public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> config = parseConfig(entity.getConfig());
            String serverUrl = (String) config.getOrDefault("serverUrl", "");
            String toolName = entity.getFunctionName();

            String result = new McpClientExecutor(mcpClientManager).execute(serverUrl, toolName, params);
            long cost = System.currentTimeMillis() - start;
            log.info("MCP 工具执行成功: {} ({}), 耗时 {}ms", entity.getName(), entity.getId(), cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("MCP 工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
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
            return JsonUtils.parseMap(parameters);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isEmpty()) return Map.of();
        try {
            return JsonUtils.parseMap(config);
        } catch (Exception e) {
            return Map.of();
        }
    }
}