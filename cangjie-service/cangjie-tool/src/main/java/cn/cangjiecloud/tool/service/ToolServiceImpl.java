package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.SkillToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.mapper.ToolMapper;
import cn.cangjiecloud.tool.util.ToolNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl extends ServiceImpl<ToolMapper, ToolEntity>
        implements IToolService {

    private final ApplicationContext applicationContext;
    private final ToolHandlerRegistry handlerRegistry;

    @Autowired(required = false)
    private SkillToolHandler skillToolHandler;

    @Autowired(required = false)
    private cn.cangjiecloud.application.service.IApplicationService applicationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolEntity create(ToolEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        save(entity);
        log.info("工具已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolEntity update(String id, ToolEntity entity) {
        ToolEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("工具不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ToolEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        removeById(id);
        log.info("工具已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<ToolEntity> list(String keyword, String type) {
        LambdaQueryWrapper<ToolEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ToolEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ToolEntity::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(ToolEntity::getType, type);
        }
        return list(wrapper);
    }

    // ========== 工具执行（策略分发） ==========

    @Override
    public ToolExecuteResultDTO executeTool(String toolId, Map<String, Object> input) {
        ToolEntity entity = getActiveEntity(toolId);
        String resolvedType = resolveToolType(entity);

        AbsToolHandler handler = handlerRegistry.get(resolvedType);
        if (handler == null) {
            // 兜底：使用旧版 Plugin 反射执行
            return executeLegacyPlugin(entity, input);
        }
        return handler.execute(entity, input);
    }

    /**
     * 旧版 Plugin 反射执行（兜底逻辑）
     */
    private ToolExecuteResultDTO executeLegacyPlugin(ToolEntity entity, Map<String, Object> input) {
        long start = System.currentTimeMillis();
        try {
            Plugin plugin = loadPlugin(entity.getImplementation());
            PluginContext context = PluginContext.builder()
                    .params(input)
                    .metadata(Map.of(
                            "toolId", entity.getId(),
                            "toolName", entity.getName() != null ? entity.getName() : ""
                    ))
                    .build();
            Object result = plugin.execute(context);
            long cost = System.currentTimeMillis() - start;
            log.info("工具执行成功(Plugin): {} ({}), 耗时 {}ms", entity.getName(), entity.getId(), cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

    // ========== ToolSpecification（Function Calling 用） ==========

    @Override
    public List<ToolSpecification> getToolSpecifications(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolEntity> tools = listByIds(toolIds);
        if (tools.isEmpty()) {
            return Collections.emptyList();
        }

        // 只处理激活的工具，按类型分组
        Map<String, List<ToolEntity>> grouped = tools.stream()
                .filter(t -> ToolConstants.STATUS_ACTIVE.equals(t.getStatus()))
                .collect(Collectors.groupingBy(this::resolveToolType));

        List<ToolSpecification> result = new ArrayList<>();
        for (Map.Entry<String, List<ToolEntity>> entry : grouped.entrySet()) {
            AbsToolHandler handler = handlerRegistry.get(entry.getKey());
            if (handler != null) {
                result.addAll(handler.buildToolSpecifications(entry.getValue()));
            } else {
                // 没有对应 handler 时使用基础构建
                result.addAll(entry.getValue().stream()
                        .map(this::buildBasicSpec)
                        .toList());
            }
        }
        return result;
    }

    private ToolSpecification buildBasicSpec(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(ToolNaming.buildToolName(entity.getId()))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(entity.getType())
                .parameters(parseJsonMap(entity.getParameters()))
                .build();
    }

    @Override
    public String executeToolCall(String toolName, Map<String, Object> arguments) {
        String toolId = ToolNaming.parse(toolName);
        ToolEntity entity = getById(toolId);
        if (entity == null) {
            return com.alibaba.fastjson.JSON.toJSONString(Map.of("success", false, "error", "工具不存在"));
        }

        String resolvedType = resolveToolType(entity);
        AbsToolHandler handler = handlerRegistry.get(resolvedType);
        if (handler == null) {
            log.warn("不支持的工���类型: toolName={}, toolType={}", toolName, resolvedType);
            return com.alibaba.fastjson.JSON.toJSONString(Map.of("success", false, "error", "不支持的工具类型"));
        }

        var result = handler.execute(entity, arguments);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            return result.getOutput() != null ? result.getOutput().toString() : "";
        }
        return com.alibaba.fastjson.JSON.toJSONString(Map.of("success", false, "error", result.getError()));
    }

    // ========== 被 ToolProviderServiceImpl 调用 ==========

    /**
     * 获取 ApplicationService（供 Provider 使用）
     */
    public cn.cangjiecloud.application.service.IApplicationService getApplicationService() {
        return applicationService;
    }

    // ========== 内部工具方法 ==========

    private ToolEntity getActiveEntity(String toolId) {
        ToolEntity entity = getById(toolId);
        if (entity == null) {
            throw new ApiException("工具不存在");
        }
        if (!ToolConstants.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ApiException("工具未激活");
        }
        return entity;
    }

    private String resolveToolType(ToolEntity entity) {
        // 优先使用新 toolType 字段
        if (StringUtils.hasText(entity.getToolType())) {
            return entity.getToolType();
        }
        // 兼容旧 type 字段
        if (StringUtils.hasText(entity.getType())) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return com.alibaba.fastjson.JSON.parseObject(json);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 通过 implementation 全限定类名加载 Plugin：
     * 1. 优先从 Spring 容器获取 bean；
     * 2. 容器中不存在时反射实例化。
     */
    private Plugin loadPlugin(String className) {
        if (!StringUtils.hasText(className)) {
            throw new ApiException("工具未配置实现类");
        }
        try {
            Class<?> clazz = Class.forName(className);
            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new ApiException("实现类未实现 Plugin 接口: " + className);
            }
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) clazz;
            try {
                return applicationContext.getBean(pluginClass);
            } catch (NoSuchBeanDefinitionException e) {
                return pluginClass.getDeclaredConstructor().newInstance();
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("加载插件失败: " + className + ", " + e.getMessage());
        }
    }
}
