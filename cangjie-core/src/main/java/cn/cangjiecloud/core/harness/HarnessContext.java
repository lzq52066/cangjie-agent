package cn.cangjiecloud.core.harness;

import java.util.List;

/**
 * Agent 运行时状态（一次 run 的全部可变上下文）
 * <p>
 * 消息装配、轮次/步骤计数、token 累计、审批挂起点都集中在此，使"如何构造会话消息"
 * 只有一处实现，便于回放与断点续跑。
 */
public class HarnessContext {

    private final HarnessRequest request;

    /** 会话消息（初始来自上下文管线，之后按轮次追加） */
    private final List<cn.cangjiecloud.core.model.ChatMessage> messages = new java.util.ArrayList<>();

    private String runId;
    private int round;
    private int stepNo;
    private long startMs = System.currentTimeMillis();

    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private int toolCallCount;

    private String finalText;
    private String finishReason;
    private String errorMessage;

    /** 本轮模型返回但尚未全部执行完的工具调用（审批挂起时需要持久化） */
    private List<cn.cangjiecloud.core.model.ChatResponse.ToolCall> pendingToolCalls = List.of();

    /** pendingToolCalls 中已执行的个数 */
    private int pendingIndex;

    private ApprovalRequest pendingApproval;

    /** 等待调用方环境执行的本地工具调用（与审批挂起互斥） */
    private LocalToolCall pendingLocalTool;

    /**
     * 已获人工放行的 tool_call id（恢复执行时由引擎重建）。
     * <p>
     * 不随检查点持久化：审批结论只对本次挂起的那一个调用有效。
     */
    private final java.util.Set<String> approvedCallIds = new java.util.HashSet<>();

    /** 已消耗预算（子 Agent 与父共享时由父传入） */
    private long spentTokens;

    public HarnessContext(HarnessRequest request) {
        this.request = request;
        this.runId = request.getRunId();
        if (request.getContextMessages() != null) {
            messages.addAll(request.getContextMessages());
        }
    }

    public HarnessRequest getRequest() {
        return request;
    }

    public List<cn.cangjiecloud.core.model.ChatMessage> messages() {
        return messages;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSessionId() {
        return request.getSessionId();
    }

    public String getApplicationId() {
        return request.getApplicationId();
    }

    public String getTraceId() {
        return request.getTraceId();
    }

    public int getRound() {
        return round;
    }

    public int nextRound() {
        return ++round;
    }

    public int getStepNo() {
        return stepNo;
    }

    public int nextStepNo() {
        return ++stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    public long getStartMs() {
        return startMs;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public String getFinalText() {
        return finalText;
    }

    public void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<cn.cangjiecloud.core.model.ChatResponse.ToolCall> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public void setPendingToolCalls(List<cn.cangjiecloud.core.model.ChatResponse.ToolCall> pendingToolCalls) {
        this.pendingToolCalls = pendingToolCalls == null ? List.of() : pendingToolCalls;
        this.pendingIndex = 0;
    }

    public int getPendingIndex() {
        return pendingIndex;
    }

    public void advancePendingIndex() {
        pendingIndex++;
    }

    public ApprovalRequest getPendingApproval() {
        return pendingApproval;
    }

    public void setPendingApproval(ApprovalRequest pendingApproval) {
        this.pendingApproval = pendingApproval;
    }

    public LocalToolCall getPendingLocalTool() {
        return pendingLocalTool;
    }

    public void setPendingLocalTool(LocalToolCall pendingLocalTool) {
        this.pendingLocalTool = pendingLocalTool;
    }

    /**
     * 标记某个工具调用已获人工放行（仅对本次 run 有效，不落检查点）
     */
    public void approve(String callId) {
        if (callId != null) {
            approvedCallIds.add(callId);
        }
    }

    public boolean isApproved(String callId) {
        return callId != null && approvedCallIds.contains(callId);
    }

    /**
     * 本次挂起所对应的工具调用（审批单展示与放行时使用）
     */
    public cn.cangjiecloud.core.model.ChatResponse.ToolCall currentPendingCall() {
        int index = getPendingIndex();
        List<cn.cangjiecloud.core.model.ChatResponse.ToolCall> calls = getPendingToolCalls();
        return calls == null || index < 0 || index >= calls.size() ? null : calls.get(index);
    }

    public long getSpentTokens() {
        return spentTokens;
    }

    public boolean isCancelled() {
        return request.isCancelled();
    }

    public boolean budgetExceeded() {
        HarnessConfig config = request.getConfig();
        if (config == null || config.getRunTokenBudget() == null || config.getRunTokenBudget() <= 0) {
            return false;
        }
        return spentTokens >= config.getRunTokenBudget();
    }

    /**
     * 追加一轮模型输出对应的 assistant 消息。
     * <p>
     * 除文本外，若本轮发起了工具调用，还需把 tool_calls（id/name/arguments）一并落到消息里：
     * 后续 tool 结果消息必须紧跟在带 tool_calls 的 assistant 消息之后，厂商才会接受；
     * 断点续跑（本地工具/人工审批）跨请求重建上下文时尤其依赖这里持久化的 tool_calls。
     */
    public void addAssistant(AssistantTurn turn) {
        java.util.List<cn.cangjiecloud.core.model.ChatMessage.ToolCallRef> refs = null;
        if (turn.hasToolCalls()) {
            refs = turn.getToolCalls().stream()
                    .map(tc -> cn.cangjiecloud.core.model.ChatMessage.ToolCallRef.of(
                            tc.getId(), tc.getName(), tc.getArguments()))
                    .toList();
        }
        messages.add(cn.cangjiecloud.core.model.ChatMessage
                .assistant(turn.getContent() == null ? "" : turn.getContent(), refs));
        inputTokens += turn.getInputTokens();
        outputTokens += turn.getOutputTokens();
        totalTokens += turn.tokensOrZero();
        spentTokens += turn.tokensOrZero();
        if (turn.getFinishReason() != null) {
            finishReason = turn.getFinishReason();
        }
    }

    /**
     * 追加一次工具调用的结果消息
     */
    public void addToolResult(ToolInvocation invocation, ToolOutcome outcome) {
        messages.add(cn.cangjiecloud.core.model.ChatMessage.tool(
                invocation.getCallName(), invocation.getCallId(), outcome.toModelContent(invocation)));
        toolCallCount++;
    }

    /**
     * 生成可持久化的检查点
     */
    public ResumeState snapshot() {
        return ResumeState.builder()
                .runId(runId)
                .round(round)
                .stepNo(stepNo)
                .messages(new java.util.ArrayList<>(messages))
                .pendingToolCalls(new java.util.ArrayList<>(pendingToolCalls))
                .pendingIndex(pendingIndex)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .toolCallCount(toolCallCount)
                .spentTokens(spentTokens)
                .elapsedMs(System.currentTimeMillis() - startMs)
                .build();
    }

    /**
     * 从检查点恢复（保留原 startMs 语义由调用方修正）
     */
    public static HarnessContext restore(HarnessRequest request, ResumeState state) {
        HarnessContext ctx = new HarnessContext(request);
        ctx.messages.clear();
        if (state.getMessages() != null) {
            ctx.messages.addAll(state.getMessages());
        }
        ctx.round = state.getRound();
        ctx.stepNo = state.getStepNo();
        ctx.inputTokens = state.getInputTokens();
        ctx.outputTokens = state.getOutputTokens();
        ctx.totalTokens = state.getTotalTokens();
        ctx.toolCallCount = state.getToolCallCount();
        ctx.spentTokens = state.getSpentTokens();
        ctx.pendingToolCalls = state.getPendingToolCalls() == null
                ? List.of() : new java.util.ArrayList<>(state.getPendingToolCalls());
        ctx.pendingIndex = state.getPendingIndex();
        ctx.startMs = System.currentTimeMillis() - state.getElapsedMs();
        ctx.runId = state.getRunId();
        return ctx;
    }
}
