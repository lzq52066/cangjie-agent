package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 审批单查询接口（管理侧只读）。
 * <p>
 * 决策与恢复执行在对话侧：{@code POST /api/chat/approval/{approvalId}/decide}，
 * 由对话侧持有引擎与检查点，观测模块只负责存单与幂等改单。
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/observability/agent-approvals")
public class AgentApprovalController {

    private final IAgentApprovalService approvalService;

    /**
     * 审批单分页查询
     */
    @GetMapping
    public R<IPage<AgentApprovalEntity>> page(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String appId,
                                              @RequestParam(required = false) String userId,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        return R.data(approvalService.pageQuery(status, appId, userId, pageNum, pageSize));
    }

    /**
     * 审批单详情
     */
    @GetMapping("/{id}")
    public R<AgentApprovalEntity> detail(@PathVariable String id) {
        AgentApprovalEntity entity = approvalService.getById(id);
        return entity != null ? R.data(entity) : R.fail("审批单不存在");
    }
}
