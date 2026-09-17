package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.core.harness.AgentRunRecorder;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.AgentRunQueryDTO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Agent run 留痕与查询服务（同时是 core {@link AgentRunRecorder} SPI 的实现）
 */
public interface IAgentRunService extends IService<AgentRunEntity>, AgentRunRecorder {

    /**
     * 分页查询 run
     */
    IPage<AgentRunEntity> pageQuery(AgentRunQueryDTO query);

    /**
     * run 详情（含步骤、审批单、子 run）
     */
    AgentRunDetailVO detail(String runId);

    /**
     * 按 run 查询步骤明细（按 step_no 升序）
     */
    List<AgentRunStepEntity> listSteps(String runId);

    /**
     * 按会话查询 run 列表（时间升序，用于会话级回放）
     */
    List<AgentRunEntity> listBySession(String sessionId);
}
