package cn.cangjiecloud.observability.dto;

import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import lombok.Data;

import java.util.List;

/**
 * Agent run 详情（主记录 + 步骤明细 + 子 run + 审批单）
 */
@Data
public class AgentRunDetailVO {

    private AgentRunEntity run;

    private List<AgentRunStepEntity> steps;

    private List<AgentApprovalEntity> approvals;

    /** 子 Agent run（一层展开） */
    private List<AgentRunEntity> children;
}
