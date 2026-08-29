package cn.cangjiecloud.knowledge.api.dto;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateDTO {

    private String name;
    private String description;
    private String splitStrategy;
    private Integer chunkSize;
    /** 自定义分隔符列表（JSON 数组字符串，如 ["h2","blank_line"]） */
    private String separators;
    private String embeddingModelId;
    private String directory;
    /** 可见性：public / private */
    private String visibility;
}
