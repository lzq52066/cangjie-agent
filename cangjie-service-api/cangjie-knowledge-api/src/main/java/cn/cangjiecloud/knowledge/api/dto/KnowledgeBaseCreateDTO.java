package cn.cangjiecloud.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseCreateDTO {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    /** 切片策略：sentence / structural / token */
    private String splitStrategy = "sentence";

    /** 切片大小（字符数） */
    private Integer chunkSize = 500;

    /** 切片重叠量 */
    private Integer chunkOverlap = 50;

    /** 嵌入模型 ID */
    private String embeddingModelId;

    /** 目录结构（JSON） */
    private String directory;
}
