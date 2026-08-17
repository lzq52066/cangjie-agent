package cn.cangjiecloud.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "model_config", autoResultMap = true)
public class ModelEntity extends BaseEntity {

    /** 模型名称（显示名） */
    private String name;

    /** 模型类型：openai / qwen / zhipu / wenxin / ollama / custom */
    private String modelType;

    /** API Key */
    private String apiKey;

    /** API Base URL */
    private String baseUrl;

    /** 模型标识（如 gpt-4o、qwen-max、glm-4） */
    private String modelName;

    /** 温度 */
    private Double temperature;

    /** 最大 token */
    private Integer maxTokens;

    /** top_p */
    private Double topP;

    /** 是否默认模型 */
    private Boolean isDefault;

    /** 是否支持嵌入 */
    private Boolean supportEmbedding;

    /** 嵌入维度 */
    private Integer embeddingDimension;

    /** 状态：active / inactive */
    private String status;

    /** 描述 */
    private String description;
}
