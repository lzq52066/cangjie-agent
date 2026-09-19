package cn.cangjiecloud.tool.service.impl;

import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.service.IToolProviderService;
import cn.cangjiecloud.tool.service.ToolServiceImpl;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 工具提供者服务 — 将 ToolEntity 转为 LLM 可用的 ToolSpecification
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolProviderServiceImpl implements IToolProviderService {

    private final ToolHandlerRegistry handlerRegistry;
    private final ToolServiceImpl toolService;

    @Override
    public List<ToolSpecification> getToolSpecifications(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolEntity> tools = toolService.listByIds(toolIds);
        if (tools.isEmpty()) {
            return Collections.emptyList();
        }

        // 只返回激活的工具
        List<ToolEntity> activeTools = tools.stream()
                .filter(t -> ToolConstants.STATUS_ACTIVE.equals(t.getStatus()))
                .toList();

        // 按 toolType 分组构建
        Map<String, List<ToolEntity>> grouped = new HashMap<>();
        for (ToolEntity tool : activeTools) {
            String resolvedType = resolveToolType(tool);
            grouped.computeIfAbsent(resolvedType, k -> new ArrayList<>()).add(tool);
        }

        List<ToolSpecification> result = new ArrayList<>();
        for (Map.Entry<String, List<ToolEntity>> entry : grouped.entrySet()) {
            AbsToolHandler handler = handlerRegistry.get(entry.getKey());
            if (handler != null) {
                for (ToolEntity entity : entry.getValue()) {
                    try {
                        result.add(handler.buildToolSpecification(entity));
                    } catch (Exception e) {
                        log.warn("构建工具规格失败: {} ({})", entity.getName(), entity.getId(), e);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public String executeToolCall(String toolName, Map<String, Object> arguments) {
        // 按 function calling 名称反查工具（自定义函数名优先，兼容历史 tool_<id>）
        ToolEntity entity = toolService.resolveByCallName(toolName);
        if (entity == null) {
            log.warn("工具不存在: toolName={}", toolName);
            return JsonUtils.toJSONString(Map.of("success", false, "error", "工具不存在: " + toolName));
        }

        String toolType = resolveToolType(entity);
        AbsToolHandler handler = handlerRegistry.get(toolType);
        if (handler == null) {
            log.warn("不支持的工具类型: toolName={}, toolType={}", toolName, toolType);
            return JsonUtils.toJSONString(Map.of("success", false, "error", "不支持的工具类型"));
        }

        var result = handler.execute(entity, arguments);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result.getOutput() != null ? result.getOutput().toString() : "";
        }
        return JsonUtils.toJSONString(Map.of("success", false, "error", result.getError()));
    }

    private String resolveToolType(ToolEntity entity) {
        if (StringUtils.hasText(entity.getType())) {
            // 兼容旧数据：将 "tool" / "function" / "api" 映射到新类型
            return switch (entity.getType()) {
                case "HTTP", "http" -> ToolConstants.ToolType.HTTP;
                case "CUSTOM", "custom" -> ToolConstants.ToolType.CUSTOM;
                case "MCP", "mcp" -> ToolConstants.ToolType.MCP;
                case "SKILL", "skill" -> ToolConstants.ToolType.SKILL;
                default -> ToolConstants.ToolType.PLUGIN;
            };
        }
        return ToolConstants.ToolType.PLUGIN;
    }
}