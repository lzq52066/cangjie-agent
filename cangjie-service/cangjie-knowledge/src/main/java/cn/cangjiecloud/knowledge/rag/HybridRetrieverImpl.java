package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器实现
 * <p>
 * 同时执行向量相似度检索和全文检索，使用加权分数融合排序。
 * 支持相似度阈值过滤和动态 topK（实际结果数不强制凑满）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrieverImpl implements HybridRetriever {

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;

    /** 加权融合中向量分数的权重（可配置：cangjie.retrieval.vector-weight） */
    @Value("${cangjie.retrieval.vector-weight:0.7}")
    private double vectorWeight;

    /** 加权融合中全文检索分数的权重（可配置：cangjie.retrieval.fulltext-weight） */
    @Value("${cangjie.retrieval.fulltext-weight:0.3}")
    private double fullTextWeight;

    /** 候选倍数，用于融合前各通道多取一些 */
    private static final int CANDIDATE_MULTIPLIER = 3;

    @Override
    public List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK, double similarityThreshold) {
        return retrieve(query, List.of(knowledgeBaseId), topK, similarityThreshold);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK, double similarityThreshold) {
        if (query == null || query.isBlank() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 各通道取更多候选用于融合
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

        // 3. 加权融合 + 阈值过滤 + 动态 topK
        return weightedFusion(vectorResults, fullTextResults, topK, similarityThreshold);
    }

    /**
     * 加权分数融合
     * <p>
     * 对每条结果计算：finalScore = vectorWeight * vectorScore + fullTextWeight * normalizedFullTextScore
     * <ul>
     *   <li>向量分数（cosine similarity）本身即为 [0,1]，无需归一化</li>
     *   <li>全文分数（ts_rank）无上界，按批次最大值归一化到 [0,1]</li>
     *   <li>不修改输入对象的 vectorScore / fullTextScore，仅写 finalScore</li>
     * </ul>
     */
    private List<RetrievalResult> weightedFusion(List<RetrievalResult> vectorResults,
                                                  List<RetrievalResult> fullTextResults,
                                                  int topK,
                                                  double similarityThreshold) {
        // ---- 阶段 1: 按段落 ID 收集分数和来源对象 ----
        Map<String, RetrievalResult> sourceMap = new LinkedHashMap<>();
        Map<String, Double> vecScoreMap = new HashMap<>();
        Map<String, Double> ftScoreMap = new HashMap<>();

        for (RetrievalResult r : vectorResults) {
            String key = r.getParagraphId();
            sourceMap.putIfAbsent(key, r);
            vecScoreMap.merge(key, r.getVectorScore(), Math::max);
        }
        for (RetrievalResult r : fullTextResults) {
            String key = r.getParagraphId();
            sourceMap.putIfAbsent(key, r);
            ftScoreMap.merge(key, r.getFullTextScore(), Math::max);
        }

        // ---- 阶段 2: 全文检索分数归一化（ts_rank 无上界 → [0,1]） ----
        double maxFT = ftScoreMap.values().stream()
                .mapToDouble(Double::doubleValue)
                .max().orElse(1.0);

        // ---- 阶段 3: 计算加权融合分数（vecScore 已是 [0,1] 无需归一化） ----
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(vecScoreMap.keySet());
        allIds.addAll(ftScoreMap.keySet());

        Map<String, Double> finalScoreMap = new LinkedHashMap<>();
        for (String id : allIds) {
            double vec = vecScoreMap.getOrDefault(id, 0.0);
            double ft = ftScoreMap.getOrDefault(id, 0.0);
            double normalizedFT = maxFT > 0 ? ft / maxFT : 0;
            finalScoreMap.put(id, vectorWeight * vec + fullTextWeight * normalizedFT);
        }

        // ---- 阶段 4: 阈值过滤 + 动态 topK ----
        return finalScoreMap.entrySet().stream()
                .filter(e -> similarityThreshold <= 0 || e.getValue() >= similarityThreshold)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    RetrievalResult r = sourceMap.get(e.getKey());
                    r.setFinalScore(e.getValue());
                    // 不改写 vectorScore / fullTextScore，保留原始分数语义
                    return r;
                })
                .collect(Collectors.toList());
    }
}