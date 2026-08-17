package cn.cangjiecloud.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "chat_session", autoResultMap = true)
public class ChatSessionEntity extends BaseEntity {

    /** 应用 ID */
    private String applicationId;

    /** 会话 ID（业务唯一标识） */
    private String sessionId;

    /** 会话标题 */
    private String title;

    /** 用户 ID */
    private String userId;

    /** 来源：web / api / wechat / dingtalk / feishu */
    private String source;

    /** 模型 ID */
    private String modelId;

    /** 状态：active / closed */
    private String status;

    /** 消息数量 */
    private Integer messageCount;

    /** 已使用 token 数 */
    private Integer tokensUsed;

    /** 元数据（JSON） */
    private String metadata;
}
