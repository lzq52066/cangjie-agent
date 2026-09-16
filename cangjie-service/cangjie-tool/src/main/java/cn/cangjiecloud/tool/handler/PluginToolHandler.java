package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Plugin 反射工具处理器 — 兼容旧版 Plugin 接口
 */
@Slf4j
@Component
@ToolHandlerType(ToolConstants.ToolType.PLUGIN)
public class PluginToolHandler extends AbsToolHandler {

    private final ApplicationContext applicationContext;

    public PluginToolHandler(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public ToolSpecification buildToolSpecification(ToolEntity entity) {
        return ToolSpecification.builder()
                .toolId(entity.getId())
                .name(specName(entity))
                .description(entity.getDescription() != null ? entity.getDescription() : entity.getName())
                .toolType(ToolConstants.ToolType.PLUGIN)
                .parameters(parseParameters(entity.getParameters()))
                .build();
    }

    @Override
    public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            Plugin plugin = loadPlugin(entity.getImplementation());
            PluginContext context = PluginContext.builder()
                    .params(params)
                    .metadata(Map.of(
                            "toolId", entity.getId(),
                            "toolName", entity.getName() != null ? entity.getName() : ""
                    ))
                    .build();
            Object result = plugin.execute(context);
            long cost = System.currentTimeMillis() - start;
            log.info("Plugin 工具执行成功: {} ({}), 耗时 {}ms", entity.getName(), entity.getId(), cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("Plugin 工具执行失败: {} ({})", entity.getName(), entity.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        try {
            return com.alibaba.fastjson.JSON.parseObject(parameters);
        } catch (Exception e) {
            return Map.of();
        }
    }
}