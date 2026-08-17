package cn.cangjiecloud.trigger.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建渠道请求
 */
@Data
public class ChannelCreateDTO {

    /** 渠道名称 */
    @NotBlank(message = "渠道名称不能为空")
    private String name;

    /** 渠道类型：wechat / wechat_mp / wechat_work / dingtalk / feishu */
    @NotBlank(message = "渠道类型不能为空")
    private String type;

    /** 关联应用 ID */
    private String applicationId;

    /** 平台应用 ID */
    private String appId;

    /** 平台应用密钥 */
    private String appSecret;

    /** 验证 Token */
    private String token;

    /** 消息加解密 Key */
    private String encodingAesKey;

    /** 平台验证 Token（钉钉/飞书等） */
    private String verifyToken;

    /** JSON 扩展配置 */
    private String config;

    /** 状态：active / inactive */
    private String status;
}
