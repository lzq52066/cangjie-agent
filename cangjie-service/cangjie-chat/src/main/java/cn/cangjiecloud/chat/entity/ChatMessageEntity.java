package cn.cangjiecloud.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessageEntity extends BaseEntity {

    /** 会话 ID（业务唯一标识） */
    private String sessionId;

    /** 应用 ID */
    private String applicationId;

    /** 角色：user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** token 数 */
    private Integer tokens;

    /** 检索来源（JSON） */
    private String retrievalSources;

    /** 元数据（JSON） */
    private String metadata;

    /** 响应耗时（毫秒） */
    private Long duration;
}
