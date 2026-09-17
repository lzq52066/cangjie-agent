package cn.cangjiecloud.core.harness;

import java.util.List;

/**
 * Agent 执行过程事件监听器。
 * <p>
 * 同步对话使用 {@link #NOOP}，流式对话使用 SSE 适配器，工作流与评测可各自订阅，
 * 引擎内部不感知任何传输协议。
 */
public interface HarnessListener {

    /** 一轮模型调用开始 */
    default void onRoundStart(int round) {
    }

    /** 模型内容增量（仅流式） */
    default void onDelta(String delta) {
    }

    /** 一轮模型输出结束 */
    default void onAssistantTurn(AssistantTurn turn) {
    }

    /** 工具调用开始 */
    default void onToolStart(ToolInvocation invocation) {
    }

    /** 工具调用结束 */
    default void onToolFinish(ToolInvocation invocation, ToolOutcome outcome) {
    }

    /** 上下文构建完成（各 slot 实际占用） */
    default void onContextReady(List<cn.cangjiecloud.core.harness.context.ContextFragment> fragments) {
    }

    /** run 因等待人工审批而挂起 */
    default void onWaitingApproval(ApprovalRequest request) {
    }

    /** run 结束（任何状态都会回调一次） */
    default void onComplete(HarnessOutcome outcome) {
    }

    HarnessListener NOOP = new HarnessListener() {
    };
}
