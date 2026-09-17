package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一轮模型输出的统一表示（同步与流式归一后的结果）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantTurn {

    /** 本轮模型输出内容 */
    private String content;

    /** 实际使用的模型名称 */
    private String model;

    /** 完成原因：stop / length / tool_calls ... */
    private String finishReason;

    private long inputTokens;

    private long outputTokens;

    private long totalTokens;

    /** 本轮请求的 prompt 快照（仅用于 step 留痕的增量记录） */
    private String promptDigest;

    /** 工具调用列表，为空表示本轮为最终回答 */
    private List<ChatResponse.ToolCall> toolCalls;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public long tokensOrZero() {
        return totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
    }
}
