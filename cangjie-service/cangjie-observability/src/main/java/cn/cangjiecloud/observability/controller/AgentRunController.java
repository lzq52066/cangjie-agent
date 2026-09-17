package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.AgentRunQueryDTO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.service.IAgentRunService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 执行留痕查询接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability/agent-runs")
public class AgentRunController {

    private final IAgentRunService agentRunService;

    /**
     * run 分页查询
     */
    @GetMapping
    public R<IPage<AgentRunEntity>> page(AgentRunQueryDTO query) {
        return R.data(agentRunService.pageQuery(query));
    }

    /**
     * run 详情（步骤 + 审批单 + 子 run）
     */
    @GetMapping("/{runId}")
    public R<AgentRunDetailVO> detail(@PathVariable String runId) {
        AgentRunDetailVO detail = agentRunService.detail(runId);
        return detail != null ? R.data(detail) : R.fail("Agent run 不存在");
    }

    /**
     * 步骤明细（时间线）
     */
    @GetMapping("/{runId}/steps")
    public R<List<AgentRunStepEntity>> steps(@PathVariable String runId) {
        return R.data(agentRunService.listSteps(runId));
    }

    /**
     * 按会话查询 run 时间线
     */
    @GetMapping("/timeline")
    public R<List<AgentRunEntity>> timeline(String sessionId) {
        return R.data(agentRunService.listBySession(sessionId));
    }
}
