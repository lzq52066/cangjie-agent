package cn.cangjiecloud.trigger.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 渠道消息转发请求/回复
 */
@Data
public class ChannelReplyDTO {

    /** 渠道 ID */
    @NotBlank(message = "渠道 ID 不能为空")
    private String channelId;

    /** 消息内容 */
    private String message;

    /** 外部用户 ID */
    private String openId;

    /** 消息类型：text / image / event */
    private String msgType;

    /** 事件类型 */
    private String eventType;

    /** 关联应用 ID */
    private String applicationId;
}
