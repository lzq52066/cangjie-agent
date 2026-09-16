package cn.cangjiecloud.model.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProviderUpdateDTO {

    private String name;

    @Size(max = 20, message = "厂商标识长度不能超过 20")
    private String code;

    private String baseUrl;

    /** API Key（含掩码符时视为未修改） */
    private String apiKey;

    /** 状态：active / inactive */
    private String status;

    private String description;
}
