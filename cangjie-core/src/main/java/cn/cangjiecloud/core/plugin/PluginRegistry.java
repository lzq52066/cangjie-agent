package cn.cangjiecloud.core.plugin;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 插件注册中心
 * <p>
 * 自动收集 Spring 容器中所有 {@link Plugin} 实现，按 name 注册。
 * 运行时支持按 name 或 type 查询。
 */
@Component
public class PluginRegistry {

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

    public PluginRegistry(List<Plugin> pluginList) {
        if (pluginList != null) {
            for (Plugin plugin : pluginList) {
                plugins.put(plugin.getName(), plugin);
            }
        }
    }

    /**
     * 注册插件
     */
    public void register(Plugin plugin) {
        if (plugin == null || plugin.getName() == null) {
            return;
        }
        plugins.put(plugin.getName(), plugin);
    }

    /**
     * 按 name 获取插件
     */
    public Plugin get(String name) {
        return plugins.get(name);
    }

    /**
     * 获取全部插件
     */
    public List<Plugin> list() {
        return List.copyOf(plugins.values());
    }

    /**
     * 按 type 列出插件
     */
    public List<Plugin> listByType(String type) {
        return plugins.values().stream()
                .filter(p -> type == null || type.equals(p.getType()))
                .collect(Collectors.toList());
    }
}
