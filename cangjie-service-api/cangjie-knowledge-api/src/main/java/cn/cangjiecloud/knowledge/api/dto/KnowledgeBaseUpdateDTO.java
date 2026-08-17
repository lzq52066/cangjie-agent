package cn.cangjiecloud.knowledge.api.dto;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateDTO {

    private String name;
    private String description;
    private String splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String embeddingModelId;
    private String directory;
}
