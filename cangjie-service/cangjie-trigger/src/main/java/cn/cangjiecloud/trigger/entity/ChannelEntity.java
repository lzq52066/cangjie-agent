package cn.cangjiecloud.trigger.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 渠道配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "channel", autoResultMap = true)
public class ChannelEntity extends BaseEntity {

    /** 渠道名称 */
    private String name;

    /** 渠道类型：wechat / wechat_mp / wechat_work / dingtalk / feishu */
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

    /** 最后调用时间 */
    private LocalDateTime lastCallTime;

    /** 调用次数 */
    private Integer callCount;
}
