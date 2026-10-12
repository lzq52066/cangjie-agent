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

    /** 相似度阈值，只作用于向量通道，低于此值的向量命中将被过滤（0.0 表示不过滤） */
    private Double similarityThreshold = 0.0;

    /** 检索模式：simple / two_stage；null 表示按知识库配置解析（命中测试可比对两种模式效果） */
    private String searchMode;

    /** 是否启用全文检索 */
    private Boolean enableFullText = true;

    /** 是否启用查询改写（默认 false；开启后改写器按系统配置执行） */
    private Boolean rewrite = false;

    /** 是否启用问题路召回（匹配常见问题后取关联段落，默认开启） */
    private Boolean enableProblem = true;
}