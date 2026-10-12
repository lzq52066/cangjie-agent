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
