package cn.cangjiecloud.core.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 重排序器工厂
 * <p>
 * 自动收集所有 {@link Reranker} 实现，按 {@code getType()} 注册。
 * 默认提供 {@code NoOpReranker}（type = none）。
 */
@Component
public class RerankerFactory {

    private final Map<String, Reranker> rerankers;

    public RerankerFactory(List<Reranker> rerankers) {
        this.rerankers = rerankers.stream()
                .collect(Collectors.toMap(Reranker::getType, Function.identity(), (a, b) -> a));
    }

    /**
     * 根据类型获取重排序器，找不到时返回默认 NoOp 实现
     */
    public Reranker get(String type) {
        return rerankers.getOrDefault(type == null ? "none" : type, rerankers.get("none"));
    }
}