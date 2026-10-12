package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.Reranker;
import cn.cangjiecloud.core.rag.RerankerFactory;
import cn.cangjiecloud.core.rag.RetrievalOptions;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 混合检索器实现
 * <p>
 * 依次执行向量相似度检索与全文检索两路（串行，见 {@link #simpleCandidates}），融合后排序，
 * 可选再叠一层重排序。多知识库靠一条 IN 一次拿回全局候选，不按库循环查。
 * 支持相似度阈值过滤和动态 topK（实际结果数不强制凑满）。
 * <p>
 * 检索模式：
 * <ul>
 *   <li><b>simple</b>：段落级直接混合检索</li>
 *   <li><b>two_stage</b>：文档摘要检索定候选文档 → 候选文档内段落精确检索</li>
 * </ul>
 * 调用方没显式指定模式时按知识库配置（knowledge_base.search_mode）解析，
 * 而不是写死 simple —— 此前对话主链路走 3 参重载，等于知识库上的模式设置从没生效过。
 * <p>
 * 融合默认 <b>RRF</b>（k=60，只取名次）：加权融合要把 pgroonga 分数「按当批最大值归一化」，
 * 同一条 query 在不同时刻（候选集变了）拿到的归一化分就变了，排序与阈值判定都会漂移；
 * RRF 只看名次，跨批次稳定。仍要按分数尺度加权的，把 cangjie.retrieval.fusion 配成 weighted。
 * 相似度阈值一律只作用于向量通道：pgvector 的 cosine 分是唯一有绝对语义的量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetrieverImpl implements HybridRetriever {

    /** 调用方没给 topK 时的兜底值 */
    private static final int DEFAULT_TOP_K = 5;

    /** 重排序关闭时的类型标识（与 NoOpReranker.getType() 一致） */
    private static final String RERANK_NONE = "none";

    /** 知识库检索模式本地缓存 TTL：检索是每轮对话都走的热路径，不值得每次都查库 */
    private static final long MODE_CACHE_TTL_MS = 30_000L;

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;
    private final RerankerFactory rerankerFactory;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 加权融合中向量分数的权重（可配置：cangjie.retrieval.vector-weight） */
    @Value("${cangjie.retrieval.vector-weight:0.7}")
    private double vectorWeight;

    /** 加权融合中全文检索分数的权重（可配置：cangjie.retrieval.fulltext-weight） */
    @Value("${cangjie.retrieval.fulltext-weight:0.3}")
    private double fullTextWeight;

    /** 融合方式：rrf（默认，只看名次）/ weighted（加权分数） */
    @Value("${cangjie.retrieval.fusion:rrf}")
    private String fusion;

    /** RRF 平滑常数 k（业界惯例 60） */
    @Value("${cangjie.retrieval.rrf-k:60}")
    private int rrfK;

    /** 是否开启重排序（默认关闭） */
    @Value("${cangjie.retrieval.rerank.enabled:false}")
    private boolean rerankEnabled;

    /** 重排序器类型（none / bge / cohere） */
    @Value("${cangjie.retrieval.rerank.type:none}")
    private String rerankType;

    /** 候选倍数，融合前各通道多取一些 */
    @Value("${cangjie.retrieval.candidate-multiplier:3}")
    private int candidateMultiplier;

    private final Map<String, ModeCacheEntry> modeCache = new ConcurrentHashMap<>();

    @Override
    public List<RetrievalResult> retrieve(String query, RetrievalOptions options) {
        if (query == null || query.isBlank() || options == null
                || options.getKnowledgeBaseIds() == null || options.getKnowledgeBaseIds().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> kbIds = options.getKnowledgeBaseIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (kbIds.isEmpty()) {
            return Collections.emptyList();
        }

        int topK = options.getTopK() == null || options.getTopK() <= 0 ? DEFAULT_TOP_K : options.getTopK();
        double threshold = options.getSimilarityThreshold() == null || options.getSimilarityThreshold() < 0
                ? 0.0 : options.getSimilarityThreshold();
        int multiplier = options.getCandidateMultiplier() == null || options.getCandidateMultiplier() <= 0
                ? candidateMultiplier : options.getCandidateMultiplier();
        String searchMode = options.getSearchMode() != null ? options.getSearchMode() : resolveSearchMode(kbIds);
        String fusionMode = options.getFusion() != null ? options.getFusion() : fusion;

        RetrievalContext ctx = new RetrievalContext(query, kbIds, topK, threshold,
                Math.max(topK, topK * multiplier), fusionMode,
                effectiveRerankType(options.getRerankType()),
                options.getRrfK() == null || options.getRrfK() <= 0 ? rrfK : options.getRrfK(),
                options.getVectorWeight() == null ? vectorWeight : options.getVectorWeight(),
                options.getFullTextWeight() == null ? fullTextWeight : options.getFullTextWeight());

        List<RetrievalResult> fused = RetrievalOptions.MODE_TWO_STAGE.equalsIgnoreCase(searchMode)
                ? twoStageCandidates(ctx) : simpleCandidates(ctx);
        return orderAndTruncate(ctx, fused);
    }

    /**
     * simple 模式：两路依次单查（多知识库一条 IN 拿回全局前 candidateK）后融合
     * <p>
     * 两路是串行而不是并行：并行要把查询搬到子线程，而通道查询依赖请求线程上的上下文
     * （traceId、数据权限），搬线程要么丢上下文要么得把上下文逐个搬运 —— 热路径上这点收益不值这个风险。
     */
    private List<RetrievalResult> simpleCandidates(RetrievalContext ctx) {
        List<String> failedChannels = new ArrayList<>(2);
        List<RetrievalResult> vectorResults = new ArrayList<>();
        try {
            float[] queryEmbedding = embeddingProvider.embed(ctx.query());
            vectorResults = new ArrayList<>(vectorStore.search(queryEmbedding, ctx.kbIds(), ctx.candidateK()));
        } catch (Exception e) {
            failedChannels.add(CHANNEL_VECTOR);
            log.error("向量检索失败，本次仅全文召回（无相似度阈值过滤）: {}", e.getMessage());
        }

        List<RetrievalResult> fullTextResults = new ArrayList<>();
        try {
            fullTextResults = new ArrayList<>(vectorStore.fullTextSearch(ctx.query(), ctx.kbIds(), null, ctx.candidateK()));
        } catch (Exception e) {
            failedChannels.add(CHANNEL_FULLTEXT);
            log.error("全文检索失败: {}", e.getMessage());
        }

        if (failedChannels.size() == 2) {
            // 两路都失败不是「知识库没内容」，是检索系统坏了；只留一句日志的话，
            // 上层与链路看到的是一个正常的空结果，没人会被叫去看这条 error
            throw new IllegalStateException("混合检索两路均失败: " + String.join(",", failedChannels));
        }
        return markDegraded(fuse(ctx, thresholdVector(vectorResults, ctx.threshold()), fullTextResults), failedChannels);
    }

    /** 向量通道标识（也用作降级 metadata 的取值） */
    private static final String CHANNEL_VECTOR = "vector";

    /** 全文通道标识 */
    private static final String CHANNEL_FULLTEXT = "fullText";

    /**
     * 给结果打上「哪一路挂了」的标记。
     * <p>
     * 只写日志的话降级就只存在于日志里：链路的检索 span 仍然是一条 success，评测报告也照常把这一条
     * 当正常召回计入均值，两边都不会把人引到真正坏了的那一路。
     */
    private List<RetrievalResult> markDegraded(List<RetrievalResult> results, List<String> failedChannels) {
        if (failedChannels.isEmpty() || results.isEmpty()) {
            return results;
        }
        return results.stream().map(r -> {
            Map<String, Object> metadata = r.getMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(r.getMetadata());
            metadata.put(RetrievalResult.META_FAILED_CHANNELS, failedChannels);
            return r.toBuilder().metadata(metadata).build();
        }).collect(Collectors.toList());
    }

    /**
     * two_stage 检索：摘要定候选文档（多库单查）→ 候选文档内段落精确检索
     * <p>
     * 全文通道同样带上候选文档约束并下推到 SQL：改造前是先全库取 topK 再在内存里
     * filter，候选文档命中不了就被无关文档挤掉，「两级检索」实际上只约束了向量路。
     */
    private List<RetrievalResult> twoStageCandidates(RetrievalContext ctx) {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingProvider.embed(ctx.query());
        } catch (Exception e) {
            // 向量化失败让两级的向量那一路整体无从执行，但全文不依赖向量：
            // 降级为「仅全文召回」，与 simple 模式的单路降级同一口径 —— 直接抛会把可用的一路也丢掉
            log.error("Query 向量化失败，两级检索降级为仅全文召回: {}", e.getMessage());
            return fullTextOnlyCandidates(ctx, CHANNEL_VECTOR);
        }

        List<String> candidateDocIds;
        try {
            candidateDocIds = vectorStore.searchDocumentSummaries(queryEmbedding, ctx.kbIds(), ctx.topK());
        } catch (Exception e) {
            log.warn("Two-stage 摘要检索失败，降级为段落级检索: {}", e.getMessage());
            candidateDocIds = List.of();
        }
        if (candidateDocIds == null || candidateDocIds.isEmpty()) {
            log.info("Two-stage 检索：摘要阶段未匹配到候选文档，降级为段落级检索");
            return simpleCandidates(ctx);
        }
        log.info("Two-stage 检索：摘要阶段匹配到 {} 个候选文档", candidateDocIds.size());

        List<String> failedChannels = new ArrayList<>(2);
        List<RetrievalResult> vectorResults;
        try {
            vectorResults = vectorStore.searchByDocumentIds(queryEmbedding, candidateDocIds, ctx.candidateK());
        } catch (Exception e) {
            failedChannels.add(CHANNEL_VECTOR);
            log.error("Two-stage 段落级向量检索失败，本次仅全文召回（无相似度阈值过滤）: {}", e.getMessage());
            vectorResults = new ArrayList<>();
        }
        List<RetrievalResult> fullTextResults;
        try {
            fullTextResults = vectorStore.fullTextSearch(ctx.query(), ctx.kbIds(), candidateDocIds, ctx.candidateK());
        } catch (Exception e) {
            failedChannels.add(CHANNEL_FULLTEXT);
            log.error("Two-stage 全文检索失败: {}", e.getMessage());
            fullTextResults = List.of();
        }
        if (failedChannels.size() == 2) {
            throw new IllegalStateException("Two-stage 检索两路均失败: " + String.join(",", failedChannels));
        }
        return markDegraded(fuse(ctx, thresholdVector(vectorResults, ctx.threshold()), fullTextResults), failedChannels);
    }

    /**
     * 只剩全文一路时的检索（向量通道整体不可用时）。
     * <p>
     * 全文自己再失败就没有可召回的通道了，这时才抛 —— 与 {@code simpleCandidates} 的收口一致。
     */
    private List<RetrievalResult> fullTextOnlyCandidates(RetrievalContext ctx, String failedChannel) {
        List<RetrievalResult> fullTextResults;
        try {
            fullTextResults = new ArrayList<>(
                    vectorStore.fullTextSearch(ctx.query(), ctx.kbIds(), null, ctx.candidateK()));
        } catch (Exception e) {
            log.error("全文检索同样失败，本次检索无任何可用通道: {}", e.getMessage());
            throw new IllegalStateException("混合检索两路均失败: " + failedChannel + "," + CHANNEL_FULLTEXT, e);
        }
        return markDegraded(fuse(ctx, List.of(), fullTextResults), List.of(failedChannel));
    }

    /** 阈值只裁向量通道：cosine 分有绝对语义，全文分与融合分都没有 */
    private List<RetrievalResult> thresholdVector(List<RetrievalResult> results, double threshold) {
        if (threshold <= 0 || results.isEmpty()) {
            return results;
        }
        return results.stream().filter(r -> r.getVectorScore() >= threshold).collect(Collectors.toList());
    }

    /**
     * 按配置的融合方式合并两路结果
     */
    private List<RetrievalResult> fuse(RetrievalContext ctx, List<RetrievalResult> vectorResults,
                                       List<RetrievalResult> fullTextResults) {
        return RetrievalOptions.FUSION_WEIGHTED.equalsIgnoreCase(ctx.fusion())
                ? weightedFusion(vectorResults, fullTextResults, ctx.vectorWeight(), ctx.fullTextWeight())
                : rrfFusion(vectorResults, fullTextResults, ctx.rrfK());
    }

    /**
     * 重排序 + 截断。
     * <p>
     * 改造前的条件是 {@code fused.size() > topK}：候选数不超过 topK 时整批跳过精排，
     * 于是「只召回到几条」这种最需要重排的情况反而没重排。现在只要开了重排就走，
     * 条数控制交给重排序器与后面的 limit。
     */
    private List<RetrievalResult> orderAndTruncate(RetrievalContext ctx, List<RetrievalResult> fused) {
        if (fused.isEmpty()) {
            return List.of();
        }
        if (ctx.rerankType() != null) {
            try {
                Reranker reranker = rerankerFactory.get(ctx.rerankType());
                List<RetrievalResult> reranked = reranker.rerank(ctx.query(), fused, ctx.topK());
                if (reranked != null) {
                    return reranked;
                }
            } catch (Exception e) {
                log.warn("重排序失败，退回融合排序: {}", e.getMessage());
            }
        }
        return fused.stream().limit(ctx.topK()).collect(Collectors.toList());
    }

    /**
     * RRF 融合：{@code score = Σ 1 / (k + rank)}，rank 是各通道内的 1-based 名次。
     * <p>
     * 只用名次不用分数，因此不受 pgroonga 分无上界、批次间漂移的影响；两路都出现的段落
     * 自然拿到两边贡献之和，等价于「交叉验证过的排前面」。
     */
    private List<RetrievalResult> rrfFusion(List<RetrievalResult> vectorResults,
                                            List<RetrievalResult> fullTextResults, int rrfK) {
        int k = rrfK <= 0 ? 60 : rrfK;
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RetrievalResult> merged = new LinkedHashMap<>();
        accumulate(vectorResults, scores, merged, true, k);
        accumulate(fullTextResults, scores, merged, false, k);

        return sortByScore(scores, merged);
    }

    private void accumulate(List<RetrievalResult> results, Map<String, Double> scores,
                            Map<String, RetrievalResult> merged, boolean vectorChannel, int k) {
        int rank = 0;
        for (RetrievalResult r : results) {
            String key = paragraphKey(r);
            rank++;
            scores.merge(key, 1.0 / (k + rank), Double::sum);
            RetrievalResult target = merged.computeIfAbsent(key, id -> r);
            // 同一通道内重复出现（多库重叠）时取更高的通道分，且返回副本，不改入参对象
            if (vectorChannel) {
                if (r.getVectorScore() > target.getVectorScore()) {
                    merged.put(key, target.toBuilder().vectorScore(r.getVectorScore()).build());
                }
            } else if (r.getFullTextScore() > target.getFullTextScore()) {
                merged.put(key, target.toBuilder().fullTextScore(r.getFullTextScore()).build());
            }
        }
    }

    /**
     * 加权分数融合（不截断，保留完整候选集供重排序使用）
     * <p>
     * finalScore = vectorWeight * vectorScore + fullTextWeight * normalizedFullTextScore
     * <ul>
     *   <li>向量分数（cosine similarity）本身即为 [0,1]，无需归一化</li>
     *   <li>全文分数（pgroonga_score）无上界，按批次最大值归一化到 [0,1] —— 正是这个
     *       「按当批最大值」让分数随候选集漂移，所以默认融合已切到 RRF</li>
     *   <li>不修改入参对象：写 finalScore 前先 copy，否则一旦上层加结果缓存就会串味</li>
     * </ul>
     */
    private List<RetrievalResult> weightedFusion(List<RetrievalResult> vectorResults,
                                                 List<RetrievalResult> fullTextResults,
                                                 double vectorWeight, double fullTextWeight) {
        Map<String, Double> vecScoreMap = new HashMap<>();
        Map<String, Double> ftScoreMap = new HashMap<>();
        Map<String, RetrievalResult> sourceMap = new LinkedHashMap<>();

        for (RetrievalResult r : vectorResults) {
            String key = paragraphKey(r);
            sourceMap.putIfAbsent(key, r);
            vecScoreMap.merge(key, r.getVectorScore(), Math::max);
        }
        for (RetrievalResult r : fullTextResults) {
            String key = paragraphKey(r);
            sourceMap.merge(key, r, (old, neu) -> old.toBuilder()
                    .fullTextScore(Math.max(old.getFullTextScore(), neu.getFullTextScore())).build());
            ftScoreMap.merge(key, r.getFullTextScore(), Math::max);
        }

        double maxFT = ftScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        Map<String, Double> finalScoreMap = new LinkedHashMap<>();
        Set<String> allIds = new LinkedHashSet<>(vecScoreMap.keySet());
        allIds.addAll(ftScoreMap.keySet());
        for (String id : allIds) {
            double vec = vecScoreMap.getOrDefault(id, 0.0);
            double ft = ftScoreMap.getOrDefault(id, 0.0);
            double normalizedFT = maxFT > 0 ? ft / maxFT : 0;
            finalScoreMap.put(id, vectorWeight * vec + fullTextWeight * normalizedFT);
        }
        return sortByScore(finalScoreMap, sourceMap);
    }

    /**
     * 融合分降序；同分时按向量分、段落 ID 收口，保证同一批候选的排序可复现
     */
    private List<RetrievalResult> sortByScore(Map<String, Double> scores, Map<String, RetrievalResult> sources) {
        return scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(e -> sources.get(e.getKey()).getVectorScore(), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(e -> sources.get(e.getKey()).toBuilder().finalScore(e.getValue()).build())
                .collect(Collectors.toList());
    }

    private static String paragraphKey(RetrievalResult r) {
        if (r.getParagraphId() != null) {
            return r.getParagraphId();
        }
        // 没有段落 ID 时按「文档 + 正文哈希」取键：identityHashCode 每次都不一样，
        // 两路命中的同一段落合不到一处（既重复计分，排序也不可复现）
        String content = r.getContent();
        return r.getDocumentId() + "#" + (content == null ? "" : Integer.toHexString(content.hashCode()));
    }

    /**
     * 重排类型归一：全局关掉就一律不排；调用方显式给了类型以调用方为准。
     */
    private String effectiveRerankType(String override) {
        if (override != null) {
            return RERANK_NONE.equalsIgnoreCase(override) ? null : override;
        }
        if (!rerankEnabled || rerankType == null || RERANK_NONE.equalsIgnoreCase(rerankType)) {
            return null;
        }
        return rerankType;
    }

    /**
     * 调用方未指定检索模式时按知识库配置解析：所有库配置一致就用它，
     * 混用（部分 two_stage 部分 simple）时取 simple —— 两级检索会按摘要裁候选文档，
     * 拿不一致的配置去裁另一个库的候选会直接漏召回。
     */
    private String resolveSearchMode(List<String> kbIds) {
        Set<String> modes = new HashSet<>();
        for (String kbId : kbIds) {
            modes.add(cachedSearchMode(kbId));
        }
        return modes.size() == 1 && modes.contains(RetrievalOptions.MODE_TWO_STAGE)
                ? RetrievalOptions.MODE_TWO_STAGE : RetrievalOptions.MODE_SIMPLE;
    }

    private String cachedSearchMode(String kbId) {
        long now = System.currentTimeMillis();
        ModeCacheEntry entry = modeCache.get(kbId);
        if (entry != null && entry.expireAt() > now) {
            return entry.mode();
        }
        String mode = RetrievalOptions.MODE_SIMPLE;
        try {
            KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
            if (kb != null && RetrievalOptions.MODE_TWO_STAGE.equalsIgnoreCase(kb.getSearchMode())) {
                mode = RetrievalOptions.MODE_TWO_STAGE;
            }
        } catch (Exception e) {
            log.warn("读取知识库检索模式失败，按 simple 处理: kbId={}, {}", kbId, e.getMessage());
        }
        modeCache.put(kbId, new ModeCacheEntry(mode, now + MODE_CACHE_TTL_MS));
        return mode;
    }

    /** 一次检索解析后的完整口径 */
    private record RetrievalContext(String query, List<String> kbIds, int topK, double threshold, int candidateK,
                                    String fusion, String rerankType, int rrfK, double vectorWeight,
                                    double fullTextWeight) {
    }

    private record ModeCacheEntry(String mode, long expireAt) {
    }
}
