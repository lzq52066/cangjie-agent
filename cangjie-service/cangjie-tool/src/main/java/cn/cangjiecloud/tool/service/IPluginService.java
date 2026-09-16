package cn.cangjiecloud.tool.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.tool.entity.PluginEntity;

import java.util.List;

public interface IPluginService extends IService<PluginEntity> {

    PluginEntity create(PluginEntity entity);

    PluginEntity update(String id, PluginEntity entity);

    void delete(String id);

    List<PluginEntity> list(String keyword, String type);

    /**
     * 重新加载插件（校验类是否可加载，更新 loaded / loadError 状态）
     */
    PluginEntity reload(String pluginId);

    /**
     * 扫描 Spring 容器中所有 Plugin bean，按实现类名同步落库后返回插件列表
     */
    List<PluginEntity> scanPlugins();
}
