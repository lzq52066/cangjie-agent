package cn.cangjiecloud.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 知识库问题创建/更新 DTO
 */
@Data
public class ProblemCreateDTO {

    /** 问题内容 */
    @NotBlank(message = "问题内容不能为空")
    private String content;

    /** 关联段落 ID 列表（可选） */
    private List<String> paragraphIds;
}
