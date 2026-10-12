package cn.cangjiecloud.model.api.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

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
    /** 输入单价（元 / 千 token）：传 null 表示不修改 */
    @DecimalMin(value = "0", message = "输入单价不能为负")
    private BigDecimal inputPrice;
    /** 输出单价（元 / 千 token）：传 null 表示不修改 */
    @DecimalMin(value = "0", message = "输出单价不能为负")
    private BigDecimal outputPrice;
}
