package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RerankerFactory;
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
 * <p>
 * 检索模式：
 * <ul>
 *   <li><b>simple</b>：段落级直接混合检索</li>
 *   <li><b>two_stage</b>：文档摘要检索定候选文档 → 候选文档内段落精确检索</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrieverImpl implements HybridRetriever {

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;
    private final RerankerFactory rerankerFactory;

    /** 加权融合中向量分数的权重（可配置：cangjie.retrieval.vector-weight） */
    @Value("${cangjie.retrieval.vector-weight:0.7}")
    private double vectorWeight;

    /** 加权融合中全文检索分数的权重（可配置：cangjie.retrieval.fulltext-weight） */
    @Value("${cangjie.retrieval.fulltext-weight:0.3}")
    private double fullTextWeight;

    /** 是否开启重排序（默认关闭） */
    @Value("${cangjie.retrieval.rerank.enabled:false}")
    private boolean rerankEnabled;

    /** 重排序器类型（none / bge / cohere） */
    @Value("${cangjie.retrieval.rerank.type:none}")
    private String rerankType;

    /** 候选倍数，用于融合前各通道多取一些 */
    private static final int CANDIDATE_MULTIPLIER = 3;

    @Override
    public List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK, double similarityThreshold) {
        return retrieve(query, List.of(knowledgeBaseId), topK, similarityThreshold);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK, double similarityThreshold) {
        return retrieve(query, knowledgeBaseIds, topK, similarityThreshold, "simple");
    }

    @Override
    public List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK,
                                          double similarityThreshold, String searchMode) {
        if (query == null || query.isBlank() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }

        if ("two_stage".equalsIgnoreCase(searchMode)) {
            return retrieveTwoStage(query, knowledgeBaseIds, topK, similarityThreshold);
        }
        return retrieveSimple(query, knowledgeBaseIds, topK, similarityThreshold);
    }

    /**
     * 简单模式：段落级直接混合检索
     */
    private List<RetrievalResult> retrieveSimple(String query, List<String> knowledgeBaseIds, int topK, double similarityThreshold) {
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

        // 3. 加权融合
        List<RetrievalResult> fused = weightedFusion(vectorResults, fullTextResults, similarityThreshold);

        // 4. 重排序
        if (rerankEnabled && fused.size() > topK) {
            return rerankerFactory.get(rerankType).rerank(query, fused, topK);
        }

        return fused.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * Two-stage 检索：摘要检索定候选文档 → 候选文档内段落精确检索
     */
    private List<RetrievalResult> retrieveTwoStage(String query, List<String> knowledgeBaseIds, int topK,
                                                    double similarityThreshold) {
        int candidateK = topK * CANDIDATE_MULTIPLIER;

        // Stage 1: 嵌入 query 并检索文档摘要，筛选候选文档
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingProvider.embed(query);
        } catch (Exception e) {
            log.error("Query 向量化失败", e);
            return Collections.emptyList();
        }

        Set<String> candidateDocIds = new LinkedHashSet<>();
        for (String kbId : knowledgeBaseIds) {
            List<String> docsBySummary = vectorStore.searchDocumentSummaries(queryEmbedding, kbId, topK);
            candidateDocIds.addAll(docsBySummary);
        }

        if (candidateDocIds.isEmpty()) {
            log.info("Two-stage 检索：摘要阶段未匹配到候选文档，降级为段落级检索");
            return retrieveSimple(query, knowledgeBaseIds, topK, similarityThreshold);
        }

        log.info("Two-stage 检索：摘要阶段匹配到 {} 个候选文档", candidateDocIds.size());

        List<String> docIdList = new ArrayList<>(candidateDocIds);

        // Stage 2: 在候选文档范围内做向量检索
        List<RetrievalResult> vectorResults;
        try {
            vectorResults = vectorStore.searchByDocumentIds(queryEmbedding, docIdList, candidateK);
        } catch (Exception e) {
            log.warn("Two-stage 段落级向量检索失败: {}", e.getMessage());
            vectorResults = new ArrayList<>();
        }

        // Stage 2 补充：候选文档范围内的全文检索
        List<RetrievalResult> fullTextResults = new ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            try {
                List<RetrievalResult> ftResults = vectorStore.fullTextSearch(query, kbId, candidateK);
                // 过滤：仅保留 candidateDocIds 内的结果
                fullTextResults.addAll(ftResults.stream()
                        .filter(r -> candidateDocIds.contains(r.getDocumentId()))
                        .toList());
            } catch (Exception e) {
                log.warn("Two-stage 全文检索失败: {}", e.getMessage());
            }
        }

        // 加权融合
        List<RetrievalResult> fused = weightedFusion(vectorResults, fullTextResults, similarityThreshold);

        // 重排序
        if (rerankEnabled && fused.size() > topK) {
            return rerankerFactory.get(rerankType).rerank(query, fused, topK);
        }

        return fused.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * 加权分数融合（不截断，保留完整候选集供重排序使用）
     * <p>
     * 对每条结果计算：finalScore = vectorWeight * vectorScore + fullTextWeight * normalizedFullTextScore
     * <ul>
     *   <li>向量分数（cosine similarity）本身即为 [0,1]，无需归一化</li>
     *   <li>全文分数（pgroonga_score）无上界，按批次最大值归一化到 [0,1]</li>
     *   <li>不修改输入对象的 vectorScore / fullTextScore，仅写 finalScore</li>
     * </ul>
     */
    private List<RetrievalResult> weightedFusion(List<RetrievalResult> vectorResults,
                                                  List<RetrievalResult> fullTextResults,
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

        // ---- 阶段 2: 全文检索分数归一化（pgroonga_score 无上界 → [0,1]） ----
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

        // ---- 阶段 4: 仅阈值过滤，不截断（截断由调用方或重排序器处理） ----
        return finalScoreMap.entrySet().stream()
                .filter(e -> similarityThreshold <= 0 || e.getValue() >= similarityThreshold)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievalResult r = sourceMap.get(e.getKey());
                    r.setFinalScore(e.getValue());
                    // 不改写 vectorScore / fullTextScore，保留原始分数语义
                    return r;
                })
                .collect(Collectors.toList());
    }
}