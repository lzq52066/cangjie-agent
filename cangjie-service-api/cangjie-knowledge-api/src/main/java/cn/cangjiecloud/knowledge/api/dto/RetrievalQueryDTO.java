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

    /** 返回数量 */
    private Integer topK = 5;

    /** 是否启用全文检索 */
    private Boolean enableFullText = true;

    /** RRF 参数 k（默认 60） */
    private Integer rrfK = 60;
}
