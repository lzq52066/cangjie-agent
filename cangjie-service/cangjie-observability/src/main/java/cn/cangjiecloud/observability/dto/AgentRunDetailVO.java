package cn.cangjiecloud.observability.dto;

import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import lombok.Data;

import java.util.List;

/**
 * Agent run 详情（主记录 + 步骤明细 + 模型调用 + 子 run + 审批单）
 */
@Data
public class AgentRunDetailVO {

    private AgentRunEntity run;

    private List<AgentRunStepEntity> steps;

    /**
     * 本次 run 名下的全部模型调用（多轮、重试与供应商降级各产生一条）。
     * 精简行，不含 prompt/response 正文；前端展开某个步骤时按 id 单条拉取。
     */
    private List<LlmTraceEntity> llmTraces;

    private List<AgentApprovalEntity> approvals;

    /** 子 Agent run（一层展开） */
    private List<AgentRunEntity> children;
}
