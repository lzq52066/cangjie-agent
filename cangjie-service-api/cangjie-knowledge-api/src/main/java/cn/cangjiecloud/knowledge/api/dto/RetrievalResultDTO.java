package cn.cangjiecloud.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResultDTO {

    private String paragraphId;
    private String documentId;
    private String knowledgeBaseId;
    private String content;
    private double vectorScore;
    private double fullTextScore;
    private double finalScore;
    private Map<String, Object> metadata;
    private String documentName;

    /** 问题路命中的常见问题内容（未命中为 null） */
    private String matchedProblem;
}
