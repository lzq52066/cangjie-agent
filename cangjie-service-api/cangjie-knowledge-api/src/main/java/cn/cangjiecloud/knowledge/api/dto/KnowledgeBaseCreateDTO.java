package cn.cangjiecloud.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseCreateDTO {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    /** 切片策略：smart（智能分段）/ custom（自定义分段） */
    private String splitStrategy = "smart";

    /** 段落最大字符数（custom 模式使用） */
    private Integer chunkSize = 500;

    /** 自定义分隔符列表（custom 模式使用，如 ["h2","blank_line"]） */
    private String separators;

    /** 嵌入模型 ID */
    private String embeddingModelId;

    /** 目录结构（JSON） */
    private String directory;

    /** 可见性：public / private（默认 private） */
    private String visibility;
}
