package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器实现
 * <p>
 * 同时执行向量相似度检索和全文检索，使用 RRF（Reciprocal Rank Fusion）算法融合排序。
 * RRF 公式：score(d) = Σ 1 / (k + rank_i(d))，其中 k 为平滑参数（默认60）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrieverImpl implements HybridRetriever {

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;

    private static final int RRF_K = 60;
    private static final int CANDIDATE_MULTIPLIER = 3;

    @Override
    public List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK) {
        return retrieve(query, List.of(knowledgeBaseId), topK);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK) {
        if (query == null || query.isBlank() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 取更多的候选用于融合
        int candidateK = topK * CANDIDATE_MULTIPLIER;

        // 1. 向量检索
        List<RetrievalResult> vectorResults = new ArrayList<>();
        try {
            float[] queryEmbedding = embeddingProvider.embed(query);
            for (String kbId : knowledgeBaseIds) {
                vectorResults.addAll(vectorStore.search(queryEmbedding, kbId, candidateK));
            }
        } catch (Exception e) {
            log.warn("向量检索失败，仅使用全文检索: {}", e.getMessage());
        }

        // 2. 全文检索
        List<RetrievalResult> fullTextResults = new ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            try {
                fullTextResults.addAll(vectorStore.fullTextSearch(query, kbId, candidateK));
            } catch (Exception e) {
                log.warn("全文检索失败: {}", e.getMessage());
            }
        }

        // 3. RRF 融合
        return rrfFusion(vectorResults, fullTextResults, topK);
    }

    /**
     * RRF 融合算法
     */
    private List<RetrievalResult> rrfFusion(List<RetrievalResult> vectorResults,
                                            List<RetrievalResult> fullTextResults,
                                            int topK) {
        Map<String, RetrievalResult> paragraphMap = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // 向量检索结果按 vectorScore 降序排名
        List<RetrievalResult> sortedVector = vectorResults.stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::getVectorScore).reversed())
                .toList();
        for (int i = 0; i < sortedVector.size(); i++) {
            RetrievalResult r = sortedVector.get(i);
            String key = r.getParagraphId();
            paragraphMap.putIfAbsent(key, r);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(key, score, Double::sum);
        }

        // 全文检索结果按 fullTextScore 降序排名
        List<RetrievalResult> sortedFullText = fullTextResults.stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::getFullTextScore).reversed())
                .toList();
        for (int i = 0; i < sortedFullText.size(); i++) {
            RetrievalResult r = sortedFullText.get(i);
            String key = r.getParagraphId();
            RetrievalResult existing = paragraphMap.get(key);
            if (existing == null) {
                paragraphMap.put(key, r);
            } else {
                // 合并：保留两者的最高分
                existing.setFullTextScore(Math.max(existing.getFullTextScore(), r.getFullTextScore()));
            }
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(key, score, Double::sum);
        }

        // 设置最终分数并排序
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    RetrievalResult r = paragraphMap.get(e.getKey());
                    r.setFinalScore(e.getValue());
                    return r;
                })
                .collect(Collectors.toList());
    }
}
