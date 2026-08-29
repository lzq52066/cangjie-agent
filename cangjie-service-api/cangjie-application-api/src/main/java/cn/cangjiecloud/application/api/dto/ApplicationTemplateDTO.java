package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 应用模板操作 DTO
 */
@Data
public class ApplicationTemplateDTO {

    /** 保存为模板时：源应用 ID */
    @NotBlank(message = "应用 ID 不能为空")
    private String applicationId;

    /** 模板名称（为空时使用应用名） */
    private String name;

    /** 模板描述 */
    private String description;

    /** 分类 */
    private String category;
}
