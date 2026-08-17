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
}
