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

    /**
     * assistant 发起的工具调用（role = assistant 且模型要求调用工具时非空）。
     * <p>
     * 必须随消息持久化并在续跑时原样回传，否则后续 tool 消息会因缺少前置 tool_calls
     * 而被严格校验的模型拒绝（"Messages with role 'tool' must be a response to a
     * preceding message with 'tool_calls'"）。字段排在末尾，保持全参构造既有参数顺序。
     */
    private java.util.List<ToolCallRef> toolCalls;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role("system").content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role("assistant").content(content).build();
    }

    public static ChatMessage assistant(String content, java.util.List<ToolCallRef> toolCalls) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .toolCalls(toolCalls == null || toolCalls.isEmpty() ? null : toolCalls)
                .build();
    }

    /** assistant 消息内的一次工具调用（仅含回传厂商所需的最小字段） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRef {
        private String id;
        private String name;
        /** 函数参数（JSON 字符串） */
        private String arguments;

        public static ToolCallRef of(String id, String name, String arguments) {
            return ToolCallRef.builder().id(id).name(name).arguments(arguments).build();
        }
    }

    public static ChatMessage tool(String toolName, String toolCallId, String content) {
        return ChatMessage.builder().role("tool").content(content).toolCallId(toolCallId).toolName(toolName).build();
    }
}
