package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 内容 */
    private String content;

    /** 工具调用 ID（role = tool 时必填，对应 assistant 消息中的 tool_calls[].id） */
    private String toolCallId;

    /** 工具名称（role = tool 时使用） */
    private String toolName;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role("system").content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role("assistant").content(content).build();
    }

    public static ChatMessage tool(String toolName, String toolCallId, String content) {
        return ChatMessage.builder().role("tool").content(content).toolCallId(toolCallId).toolName(toolName).build();
    }
}
