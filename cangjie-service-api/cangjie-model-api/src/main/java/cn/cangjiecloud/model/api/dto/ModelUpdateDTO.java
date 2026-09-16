package cn.cangjiecloud.model.api.dto;

import lombok.Data;

@Data
public class ModelUpdateDTO {

    private String name;
    /** 关联厂商 ID：为空表示不修改关联 */
    private String providerId;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Boolean isDefault;
    private Boolean supportEmbedding;
    private Integer embeddingDimension;
    private String description;
    private String status;
}
