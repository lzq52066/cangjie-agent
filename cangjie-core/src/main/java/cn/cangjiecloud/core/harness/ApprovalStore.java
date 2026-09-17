package cn.cangjiecloud.core.harness;

import java.util.List;

/**
 * 审批单存储（SPI，由 cangjie-observability 实现）。
 * <p>
 * 审批决策必须幂等：同一 approvalId 只允许从 pending 迁出一次。
 */
public interface ApprovalStore {

    /**
     * 创建审批单并落库，返回带 approvalId 与 resumeToken 的请求
     */
    ApprovalRequest create(ApprovalRequest request);

    /**
     * 查询（不存在返回 null）
     */
    ApprovalRequest find(String approvalId);

    /**
     * 原子决策：仅当当前状态为 pending 时更新，返回是否成功
     */
    boolean decide(String approvalId, String status, String decidedBy, String remark);

    /**
     * 把逾期仍 pending 的审批单批量置为 expired，返回受影响的 runId
     */
    List<String> expireOverdue(String defaultDecision);

    /**
     * 按 run 查询待审批单
     */
    ApprovalRequest findPendingByRun(String runId);
}
