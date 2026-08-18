package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.mapper.ToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl extends ServiceImpl<ToolMapper, ToolEntity>
        implements IToolService {

    private final ApplicationContext applicationContext;

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

    @Override
    public ToolExecuteResultDTO executeTool(String toolId, Map<String, Object> input) {
        ToolEntity entity = getById(toolId);
        if (entity == null) {
            throw new ApiException("工具不存在");
        }
        if (!"active".equals(entity.getStatus())) {
            throw new ApiException("工具未激活");
        }

        long start = System.currentTimeMillis();
        try {
            Plugin plugin = loadPlugin(entity.getImplementation());
            PluginContext context = PluginContext.builder()
                    .params(input)
                    .metadata(Map.of(
                            "toolId", toolId,
                            "toolName", entity.getName() != null ? entity.getName() : ""
                    ))
                    .build();
            Object result = plugin.execute(context);
            long cost = System.currentTimeMillis() - start;
            log.info("工具执行成功: {} ({}), 耗时 {}ms", entity.getName(), toolId, cost);
            return ToolExecuteResultDTO.builder()
                    .success(true)
                    .output(result)
                    .executionTime(cost)
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("工具执行失败: {} ({})", entity.getName(), toolId, e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
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
