package cn.cangjiecloud.core.harness;

/**
 * 恢复执行时重建 {@link HarnessRequest}。
 * <p>
 * 检查点只持久化会话状态（消息、轮次、待执行工具调用），模型、工具集与采样参数属于配置态，
 * 必须由业务侧按 run 上的应用/会话信息重新装配，因此引擎通过该 SPI 反向获取。
 */
public interface ResumeResolver {

    /**
     * 依据挂起的 run 重建一次执行输入（需携带 {@code resume} 检查点）
     *
     * @param pausedRun 挂起中的 run 快照
     * @param approved  审批结论
     * @param remark    决策备注（拒绝时作为回喂模型的说明）
     */
    HarnessRequest resolve(AgentRunRecorder.PausedRun pausedRun, boolean approved, String remark);
}
