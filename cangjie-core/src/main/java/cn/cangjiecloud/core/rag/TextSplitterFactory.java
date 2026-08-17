package cn.cangjiecloud.core.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文本切片策略工厂
 * <p>
 * 自动收集所有 {@link TextSplitter} 实现，按策略类型注册。
 * 运行时根据 {@link SplitStrategy} 动态获取对应实现。
 */
@Component
public class TextSplitterFactory {

    private final Map<SplitStrategy, TextSplitter> registry = new ConcurrentHashMap<>();

    public TextSplitterFactory(List<TextSplitter> splitters) {
        for (TextSplitter splitter : splitters) {
            registry.put(splitter.getStrategy(), splitter);
        }
    }

    /**
     * 根据策略类型获取切片器
     */
    public TextSplitter get(SplitStrategy strategy) {
        TextSplitter splitter = registry.get(strategy);
        if (splitter == null) {
            throw new IllegalArgumentException("未找到切片策略实现: " + strategy);
        }
        return splitter;
    }

    /**
     * 根据策略编码获取切片器
     */
    public TextSplitter get(String code) {
        return get(SplitStrategy.of(code));
    }
}
