package cn.cangjiecloud.model.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelCreateDTO {

    @NotBlank(message = "模型名称不能为空")
    private String name;

    /** 模型类型：openai / qwen / zhipu / wenxin / ollama / custom */
    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    /** API Key */
    private String apiKey;

    /** API Base URL */
    private String baseUrl;

    /** 模型标识（如 gpt-4o、qwen-max、glm-4） */
    @NotBlank(message = "模型标识不能为空")
    private String modelName;

    /** 温度 */
    private Double temperature = 0.7;

    /** 最大 token */
    private Integer maxTokens = 4096;

    /** top_p */
    private Double topP = 1.0;

    /** 是否默认模型 */
    private Boolean isDefault = false;

    /** 是否支持嵌入 */
    private Boolean supportEmbedding = false;

    /** 嵌入维度 */
    private Integer embeddingDimension;

    /** 描述 */
    private String description;
}
