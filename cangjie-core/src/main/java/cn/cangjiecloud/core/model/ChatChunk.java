package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式对话的分片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatChunk {

    /** 本次推送的内容片段 */
    private String delta;

    /** 是否结束 */
    private boolean done;

    /** 完成原因（done=true 时有值） */
    private String finishReason;

    /** 错误信息（发生异常时设置） */
    private String error;

    /** 输入 token 数（done=true 时有值） */
    private Long inputTokens;

    /** 输出 token 数（done=true 时有值） */
    private Long outputTokens;

    /** 总 token 数（done=true 时有值） */
    private Long totalTokens;

    /** 工具调用列表（Function Calling 返回，done=true 时可能有值） */
    private java.util.List<ChatResponse.ToolCall> toolCalls;
}
