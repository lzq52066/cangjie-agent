package cn.cangjiecloud.core.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次检索的完整口径。
 * <p>
 * 改造前 {@link HybridRetriever} 有 6 个 retrieve 重载，调用方各取一个子集：topK 传了、
 * threshold 传了，但 searchMode / 融合方式 / 重排始终走全局配置 —— 于是「评测跑的检索」
 * 和「生产跑的检索」不是同一个东西，测出来的分数无法外推。收敛成一个值对象后，
 * 评测可以把生产配置整份固化进来（configSnapshot），调用方缺哪个字段就回落到全局默认。
 * <p>
 * 字段一律用包装类型：null 表示「本次不覆盖，取全局配置 / 知识库配置」，
 * 0 与 false 都是有意义的取值，不能拿来当缺省哨兵。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalOptions {

    /** 检索模式：段落级直接混合检索 */
    public static final String MODE_SIMPLE = "simple";

    /** 检索模式：先文档摘要定候选文档，再在候选文档内做段落精确检索 */
    public static final String MODE_TWO_STAGE = "two_stage";

    /** 倒数排名融合（Reciprocal Rank Fusion）：只看名次不看分量纲，跨批次排序稳定 */
    public static final String FUSION_RRF = "rrf";

    /** 加权分数融合：向量分 + 归一化全文分，依赖各通道的分数尺度 */
    public static final String FUSION_WEIGHTED = "weighted";

    /** 目标知识库；空表示不检索 */
    private List<String> knowledgeBaseIds;

    /** 返回条数上限 */
    private Integer topK;

    /**
     * 相似度阈值，只作用于向量通道。
     * <p>
     * 不再拿融合分去比阈值：加权融合分既含归一化的全文分又含权重，与 pgvector 的
     * cosine 相似度不是同一个量，同一个 threshold 在两个通道下含义会漂移。
     */
    private Double similarityThreshold;

    /** 检索模式：simple / two_stage；null 表示按知识库配置解析 */
    private String searchMode;

    /** 融合方式：rrf / weighted；null 取全局配置 */
    private String fusion;

    /** 向量通道权重（weighted 融合用） */
    private Double vectorWeight;

    /** 全文通道权重（weighted 融合用） */
    private Double fullTextWeight;

    /** RRF 平滑常数 k，业界惯例取 60 */
    private Integer rrfK;

    /** 重排序器类型：none 表示关闭；null 取全局配置 */
    private String rerankType;

    /** 每通道在融合前多取的候选倍数 */
    private Integer candidateMultiplier;

    /**
     * 便捷构造：只给知识库与 topK，其余口径取默认。
     */
    public static RetrievalOptions of(List<String> knowledgeBaseIds, int topK) {
        return RetrievalOptions.builder().knowledgeBaseIds(knowledgeBaseIds).topK(topK).build();
    }
}
