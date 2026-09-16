package cn.cangjiecloud.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 厂商（Provider）配置
 * <p>
 * 统一维护某个模型厂商的 API Key 与 Base URL，模型通过 {@code provider_id} 继承凭证，
 * 避免同厂商下多个模型重复维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "model_provider", autoResultMap = true)
public class ModelProviderEntity extends BaseEntity {

    /** 厂商名称（显示名） */
    private String name;

    /** 厂商标识，与 model_config.model_type 对应，如 openai / deepseek / qwen */
    private String code;

    /** API Base URL */
    private String baseUrl;

    /** API Key（AES 密文） */
    private String apiKey;

    /** 状态：active / inactive */
    private String status;

    /** 描述 */
    private String description;
}
