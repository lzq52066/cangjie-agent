package cn.cangjiecloud.model.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProviderCreateDTO {

    /** 厂商名称 */
    @NotBlank(message = "厂商名称不能为空")
    private String name;

    /** 厂商标识，如 openai / deepseek / 自建网关自定义值 */
    @NotBlank(message = "厂商标识不能为空")
    @Size(max = 20, message = "厂商标识长度不能超过 20")
    private String code;

    /** API Base URL */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 描述 */
    private String description;
}
