package cn.cangjiecloud.core.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 查询改写器工厂
 * <p>
 * 自动收集所有 {@link QueryRewriter} 实现，按 {@code getType()} 注册。
 * 默认提供 {@code NoOpQueryRewriter}（type = none）。
 */
@Component
public class QueryRewriterFactory {

    private final Map<String, QueryRewriter> rewriters;

    public QueryRewriterFactory(List<QueryRewriter> rewriters) {
        this.rewriters = rewriters.stream()
                .collect(Collectors.toMap(QueryRewriter::getType, Function.identity(), (a, b) -> a));
    }

    /**
     * 根据类型获取查询改写器，找不到时返回默认 NoOp 实现
     */
    public QueryRewriter get(String type) {
        return rewriters.getOrDefault(type == null ? "none" : type, rewriters.get("none"));
    }
}