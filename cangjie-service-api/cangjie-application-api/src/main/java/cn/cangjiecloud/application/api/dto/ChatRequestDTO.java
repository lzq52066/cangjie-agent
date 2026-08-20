package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequestDTO {

    /** 应用 ID */
    @NotBlank(message = "应用 ID 不能为空")
    private String applicationId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 会话 ID（可选，为空表示新会话） */
    private String sessionId;

    /** 来源：web / api / wechat / dingtalk / feishu */
    private String source = "api";

    /** 用户 ID（匿名用户可传自定义标识，用于隔离会话与消息历史） */
    private String userId;

    /** 是否流式返回 */
    private Boolean stream = false;

    /**
     * OpenAI 兼容：完整对话消息列表（含当前消息）。
     * 非空时优先作为本次模型调用的完整上下文（忽略数据库历史消息）。
     */
    private List<ConversationMessage> messages;

    @Data
    public static class ConversationMessage {
        /** 角色：system / user / assistant */
        private String role;
        /** 消息内容 */
        private String content;
    }
}
