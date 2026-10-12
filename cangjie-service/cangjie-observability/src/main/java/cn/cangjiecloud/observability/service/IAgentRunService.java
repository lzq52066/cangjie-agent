package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Agent run 留痕与查询服务（同时是 core {@link AgentRunRecorder} SPI 的实现）
 */
public interface IAgentRunService extends IService<AgentRunEntity>, AgentRunRecorder {

    /**
     * run 详情（含步骤、审批单、子 run）
     */
    AgentRunDetailVO detail(String runId);

    /**
     * 按 run 查询步骤明细（按 step_no 升序）
     */
    List<AgentRunStepEntity> listSteps(String runId);

    /**
     * 放弃等待审批的 run：仅当状态为 waiting_approval 时置为 failed 并清除检查点，
     * 供审批过期扫描任务调用。轮次/token 等统计在挂起检查点时已落库，此处不覆盖。
     *
     * @return 是否成功迁移（false 表示 run 已被决策恢复或不存在）
     */
    boolean abandonWaitingRun(String runId, String finishReason, String errorMessage);
}
