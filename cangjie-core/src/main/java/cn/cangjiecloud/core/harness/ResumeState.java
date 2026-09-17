package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 断点续跑检查点。
 * <p>
 * 序列化为 JSON 存入 {@code agent_run.context_snapshot}，使 run 可以跨请求、跨进程恢复
 * （审批等待、失败重试、定时唤醒），而不是靠占住线程来等待外部输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeState {

    private String runId;

    private int round;

    private int stepNo;

    /** 已装配的完整会话消息 */
    private List<ChatMessage> messages;

    /** 本轮尚未执行完的工具调用 */
    private List<ChatResponse.ToolCall> pendingToolCalls;

    /** pendingToolCalls 中已执行的个数 */
    private int pendingIndex;

    private long inputTokens;

    private long outputTokens;

    private long totalTokens;

    private int toolCallCount;

    private long spentTokens;

    /** 已累计的执行耗时（毫秒），恢复后继续受总超时约束 */
    private long elapsedMs;
}
