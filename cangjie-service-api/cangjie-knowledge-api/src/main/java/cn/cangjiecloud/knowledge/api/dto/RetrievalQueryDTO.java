package cn.cangjiecloud.knowledge.api.dto;

import lombok.Data;

@Data
public class RetrievalQueryDTO {

    /** 查询文本 */
    private String query;

    /** 知识库 ID（单库检索时使用） */
    private String knowledgeBaseId;

    /** 知识库 ID 列表（多库检索时使用） */
    private java.util.List<String> knowledgeBaseIds;

    /** 返回数量上限 */
    private Integer topK = 5;

    /** 相似度阈值，低于此值的结果将被过滤（0.0 表示不过滤） */
    private Double similarityThreshold = 0.0;

    /** 是否启用全文检索 */
    private Boolean enableFullText = true;
}