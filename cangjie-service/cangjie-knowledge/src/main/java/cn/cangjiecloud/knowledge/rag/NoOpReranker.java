package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认无操作重排序器
 * <p>
 * 直接按粗排 finalScore 截断返回 topK，与改造前行为完全一致。
 */
@Component
public class NoOpReranker implements Reranker {

    @Override
    public String getType() {
        return "none";
    }

    @Override
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }
}