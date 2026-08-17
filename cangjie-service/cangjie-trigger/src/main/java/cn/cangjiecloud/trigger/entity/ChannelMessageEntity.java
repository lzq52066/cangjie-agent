package cn.cangjiecloud.trigger.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道消息记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "channel_message", autoResultMap = true)
public class ChannelMessageEntity extends BaseEntity {

    /** 渠道 ID */
    private String channelId;

    /** 渠道类型 */
    private String channelType;

    /** 关联应用 ID */
    private String applicationId;

    /** 外部用户 ID */
    private String openId;

    /** 会话 ID */
    private String sessionId;

    /** 消息类型：text / image / event */
    private String msgType;

    /** 消息内容 */
    private String content;

    /** 事件类型 */
    private String eventType;

    /** 回复内容 */
    private String replyContent;

    /** 状态：processed / failed */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 处理耗时（毫秒） */
    private Long costTime;
}
