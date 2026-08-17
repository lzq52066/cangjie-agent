package cn.cangjiecloud.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParagraphDTO {

    private String id;
    private String knowledgeBaseId;
    private String documentId;
    private String content;
    private String title;
    private Integer chunkIndex;
    private Integer pageNumber;
    private Integer tokenCount;
    private String status;
}
