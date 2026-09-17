package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.ApprovalStore;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * Agent 审批服务（同时是 core {@link ApprovalStore} SPI 的实现）
 */
public interface IAgentApprovalService extends IService<AgentApprovalEntity>, ApprovalStore {

    /**
     * 审批单分页查询
     *
     * @param status  状态过滤（可空）
     * @param appId   应用过滤（可空）
     * @param userId  申请人过滤（可空）
     */
    IPage<AgentApprovalEntity> pageQuery(String status, String appId, String userId,
                                        Integer pageNum, Integer pageSize);

    /**
     * 查询会话下最新一条待审批单。
     * <p>
     * 审批令牌只随挂起那次 SSE 下发，页面刷新后前端就取不到它了；恢复卡片必须能从库里把
     * 未过期的 pending 单捞回来，否则 run 会永久停在 paused。
     */
    ApprovalRequest findPendingBySession(String sessionId);
}
