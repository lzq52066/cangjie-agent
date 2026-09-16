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
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Transactional(rollbackFor = Exception.class)
    public List<PluginEntity> scanPlugins() {
        List<Plugin> discovered = pluginRegistry.list();

        // 已登记的插件（按实现类全名索引），扫描结果按类名增量落库，页面刷新才能看到
        Map<String, PluginEntity> existingByClass = list(new LambdaQueryWrapper<PluginEntity>()
                .isNotNull(PluginEntity::getClassName)).stream()
                .filter(e -> StringUtils.hasText(e.getClassName()))
                .collect(Collectors.toMap(PluginEntity::getClassName, e -> e, (a, b) -> a));

        for (Plugin plugin : discovered) {
            // 取被代理前的真实类型，否则 CGLIB 增强类名会让「重载」里的 Class.forName 失败
            String className = AopUtils.getTargetClass(plugin).getName();
            PluginEntity entity = existingByClass.get(className);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new PluginEntity();
                entity.setClassName(className);
                entity.setStatus("active");
            }
            entity.setName(plugin.getName());
            entity.setType(plugin.getType());
            entity.setDescription(plugin.getDescription());
            entity.setLoaded(true);
            entity.setLoadError(null);
            if (!StringUtils.hasText(entity.getVersion())) {
                entity.setVersion("1.0.0");
            }
            saveOrUpdate(entity);
            if (isNew) {
                log.info("扫描发现新插件: {} ({})", entity.getName(), className);
            }
        }

        // 注册中心里已不存在的记录标记为未加载，保留人工登记的配置
        Set<String> discoveredClasses = discovered.stream()
                .map(p -> AopUtils.getTargetClass(p).getName())
                .collect(Collectors.toSet());
        for (PluginEntity stale : existingByClass.values()) {
            if (Boolean.TRUE.equals(stale.getLoaded()) && !discoveredClasses.contains(stale.getClassName())) {
                stale.setLoaded(false);
                stale.setLoadError("扫描未发现该插件，可能已被移除");
                updateById(stale);
            }
        }

        log.info("插件扫描完成，注册中心 {} 个插件", discovered.size());
        return list((String) null, null);
    }
}
