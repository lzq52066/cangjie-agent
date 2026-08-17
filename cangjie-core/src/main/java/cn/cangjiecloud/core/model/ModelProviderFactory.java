package cn.cangjiecloud.core.model;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型提供者工厂
 * <p>
 * 自动收集所有 {@link ModelProvider} 实现，按类型注册。
 * 运行时根据 {@link ModelType} 动态获取对应实现。
 */
@Component
public class ModelProviderFactory {

    private final Map<ModelType, ModelProvider> registry = new ConcurrentHashMap<>();

    public ModelProviderFactory(List<ModelProvider> providers) {
        for (ModelProvider provider : providers) {
            registry.put(provider.getType(), provider);
        }
    }

    /**
     * 根据类型获取模型提供者
     */
    public ModelProvider get(ModelType type) {
        ModelProvider provider = registry.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("未找到模型提供者: " + type);
        }
        return provider;
    }

    /**
     * 根据编码获取
     */
    public ModelProvider get(String code) {
        return get(ModelType.of(code));
    }

    /**
     * 获取所有已注册类型
     */
    public List<ModelType> listTypes() {
        return List.copyOf(registry.keySet());
    }
}
