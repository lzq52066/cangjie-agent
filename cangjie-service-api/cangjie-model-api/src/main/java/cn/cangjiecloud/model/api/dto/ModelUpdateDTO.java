package cn.cangjiecloud.model.api.dto;

import lombok.Data;

@Data
public class ModelUpdateDTO {

    private String name;
    private String apiKey;
    private String baseUrl;
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
