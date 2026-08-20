package cn.cangjiecloud.tool.service.impl;

import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.service.IToolProviderService;
import cn.cangjiecloud.tool.service.ToolServiceImpl;
import cn.cangjiecloud.tool.util.ToolNaming;
import cn.cangjiecloud.core.tool.ToolSpecification;
import com.alibaba.fastjson.JSON;
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
    public List<ToolSpecification> getToolSpecificationsByApp(String applicationId) {
        // 从应用配置中解析 toolIds 并加载
        cn.cangjiecloud.application.service.IApplicationService appService;
        try {
            appService = toolService.getApplicationService();
        } catch (Exception e) {
            log.warn("获取应用服务失败: {}", e.getMessage());
            return Collections.emptyList();
        }

        cn.cangjiecloud.application.entity.ApplicationEntity app = appService.getById(applicationId);
        if (app == null) {
            return Collections.emptyList();
        }

        List<String> toolIds = parseToolIds(app.getConfig());
        // 同时从 toolIds 字段获取（如果有）
        if (app.getToolIds() != null) {
            List<String> ids = parseStringList(app.getToolIds());
            toolIds.addAll(ids);
        }
        // 去重
        toolIds = toolIds.stream().distinct().toList();

        return getToolSpecifications(toolIds);
    }

    @Override
    public String executeToolCall(String toolName, Map<String, Object> arguments) {
        String toolId = ToolNaming.parse(toolName);

        // 先判断是否是 skill 类型的工具
        ToolEntity entity = toolService.getById(toolId);
        if (entity == null) {
            log.warn("工具不存在: toolName={}, toolId={}", toolName, toolId);
            return JSON.toJSONString(Map.of("success", false, "error", "工具不存在: " + toolId));
        }

        String toolType = resolveToolType(entity);
        AbsToolHandler handler = handlerRegistry.get(toolType);
        if (handler == null) {
            log.warn("不支持的工具类型: toolName={}, toolType={}", toolName, toolType);
            return JSON.toJSONString(Map.of("success", false, "error", "不支持的工具类型"));
        }

        var result = handler.execute(entity, arguments);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result.getOutput() != null ? result.getOutput().toString() : "";
        }
        return JSON.toJSONString(Map.of("success", false, "error", result.getError()));
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

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            return JSON.parseArray(json).stream()
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> parseToolIds(String config) {
        if (!StringUtils.hasText(config)) return new ArrayList<>();
        try {
            Map<String, Object> map = JSON.parseObject(config);
            Object toolIds = map.get("toolIds");
            if (toolIds instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        } catch (Exception e) {
            log.debug("解析 config 中的 toolIds 失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
}