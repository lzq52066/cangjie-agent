package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplicationRollbackDTO {
    @NotBlank(message = "版本 ID 不能为空")
    private String versionId;
}