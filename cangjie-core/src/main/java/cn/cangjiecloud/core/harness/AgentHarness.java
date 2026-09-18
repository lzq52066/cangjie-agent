package cn.cangjiecloud.core.harness;

/**
 * Agent 运行时（Agent Harness）—— 围绕大模型的执行支架。
 * <p>
 * 它把"推理 → 工具调用 → 结果回填 → 再推理"的循环、轮次与超时约束、上下文预算、
 * 工具权限与审批、状态检查点、每一步留痕统一收敛为一套可复用运行时，
 * 使对话、工作流节点、子 Agent 复用同一个引擎。
 * <p>
 * 实现只依赖 {@link ModelGateway}、{@link ToolGateway}、{@link AgentRunRecorder} 三个协作者接口，
 * 不感知 SSE / 数据库 / 业务模块。
 */
public interface AgentHarness {

    /**
     * 执行一次 Agent run（同步返回终态或挂起态）
     *
     * @param request  执行输入
     * @param listener 事件监听器，同步路径传 {@link HarnessListener#NOOP}
     */
    HarnessOutcome run(HarnessRequest request, HarnessListener listener);

    /**
     * 从检查点恢复执行（审批通过、失败重试、定时唤醒）
     *
     * @param runId       待恢复的 run
     * @param approved    审批结论（无审批单时传 true）
     * @param decidedBy   决策人（可空）
     * @param remark      决策备注（可空，拒绝时作为回喂模型的原因）
     * @param resumeToken 一次性恢复令牌
     * @param listener    恢复后新产生事件的监听器
     */
    HarnessOutcome resume(String runId, boolean approved, String decidedBy, String remark,
                          String resumeToken, HarnessListener listener);

    /**
     * 回传调用方环境（如浏览器）执行本地工具的结果，从检查点恢复并续跑。
     *
     * @param runId       挂起中的 run
     * @param callId      挂起时下发的 tool_call id
     * @param resultJson  本地执行结果（JSON 字符串，回填给模型；失败传 {"success":false,...}）
     * @param failed      本地执行是否失败（失败时 resultJson 作为错误信息）
     * @param resumeToken 一次性恢复令牌
     * @param listener    恢复后新产生事件的监听器
     */
    HarnessOutcome completeLocalTool(String runId, String callId, String resultJson, boolean failed,
                                     String resumeToken, HarnessListener listener);
}
