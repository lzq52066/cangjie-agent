package cn.cangjiecloud.core.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 检索结果
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {

    /**
     * {@code metadata} 的约定 key：本次检索中失败的通道名（{@code vector} / {@code fullText}）。
     * <p>
     * 一路挂了调用方拿到的仍是另一路的结果列表，看不出区别；而向量路是唯一受相似度阈值约束的通道，
     * 它挂了意味着这批结果<strong>没有任何阈值过滤</strong>。这个标记要让上层（链路 span / 评测明细）
     * 能把「降级」和「正常召回」区分开，否则排障时看到的一条成功 span 会把问题指向错的方向。
     */
    public static final String META_FAILED_CHANNELS = "retrievalFailedChannels";

    /** 段落 ID */
    private String paragraphId;

    /** 文档 ID */
    private String documentId;

    /** 知识库 ID */
    private String knowledgeBaseId;

    /** 段落内容 */
    private String content;

    /** 向量相似度分数（cosine，0~1，越高越相关；未走向量通道时为 0） */
    private double vectorScore;

    /** 全文检索分数（pgroonga 原始分，无上界；未走全文通道时为 0） */
    private double fullTextScore;

    /** 融合后的最终分数（RRF 或加权，取决于检索口径），各通道原始分数不被覆写 */
    private double finalScore;

    /** 元数据 */
    private Map<String, Object> metadata;
}
