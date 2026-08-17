package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginRegistry;
import cn.cangjiecloud.tool.entity.PluginEntity;
import cn.cangjiecloud.tool.mapper.PluginMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginServiceImpl extends ServiceImpl<PluginMapper, PluginEntity>
        implements IPluginService {

    private final PluginRegistry pluginRegistry;
    private final ApplicationContext applicationContext;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PluginEntity create(PluginEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        if (entity.getLoaded() == null) {
            entity.setLoaded(false);
        }
        save(entity);
        log.info("插件已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PluginEntity update(String id, PluginEntity entity) {
        PluginEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("插件不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        PluginEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        removeById(id);
        log.info("插件已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<PluginEntity> list(String keyword, String type) {
        LambdaQueryWrapper<PluginEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PluginEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PluginEntity::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(PluginEntity::getType, type);
        }
        return list(wrapper);
    }

    @Override
    public PluginEntity reload(String pluginId) {
        PluginEntity entity = getById(pluginId);
        if (entity == null) {
            throw new ApiException("插件不存在");
        }
        if (!StringUtils.hasText(entity.getClassName())) {
            entity.setLoaded(false);
            entity.setLoadError("未配置实现类");
            updateById(entity);
            return entity;
        }
        try {
            Class<?> clazz = Class.forName(entity.getClassName());
            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new ApiException("类未实现 Plugin 接口: " + entity.getClassName());
            }
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) clazz;
            Plugin plugin;
            try {
                plugin = applicationContext.getBean(pluginClass);
            } catch (NoSuchBeanDefinitionException e) {
                plugin = pluginClass.getDeclaredConstructor().newInstance();
            }
            pluginRegistry.register(plugin);
            entity.setLoaded(true);
            entity.setLoadError(null);
            log.info("插件重新加载成功: {} ({})", entity.getName(), pluginId);
        } catch (Exception e) {
            entity.setLoaded(false);
            entity.setLoadError(e.getMessage());
            log.error("插件加载失败: {} ({})", entity.getName(), pluginId, e);
        }
        updateById(entity);
        return entity;
    }

    @Override
    public List<PluginEntity> scanPlugins() {
        return pluginRegistry.list().stream()
                .map(plugin -> {
                    PluginEntity entity = new PluginEntity();
                    entity.setName(plugin.getName());
                    entity.setType(plugin.getType());
                    entity.setDescription(plugin.getDescription());
                    entity.setClassName(plugin.getClass().getName());
                    entity.setLoaded(true);
                    return entity;
                })
                .collect(Collectors.toList());
    }
}
