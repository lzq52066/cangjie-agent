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
@Builder
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

    /** 向量相似度分数（0~1，越高越相关） */
    private double vectorScore;

    /** 全文检索分数 */
    private double fullTextScore;

    /** RRF 融合后的最终分数 */
    private double finalScore;

    /** 元数据 */
    private Map<String, Object> metadata;
}
