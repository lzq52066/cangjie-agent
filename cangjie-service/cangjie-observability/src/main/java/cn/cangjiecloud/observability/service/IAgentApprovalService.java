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

    /**
     * 记一张「用户已经决策」的留痕审批单，直接落 approved / rejected，不经过 pending。
     * <p>
     * 用于闸门不在服务端的人工确认（浏览器侧本地文件的写/改名/删除）：那边点完允许或拒绝，
     * 服务端只有工具步骤，没有决策记录，审计上等同于「没人批过」。这类单子不能被超时扫描命中
     * （它不是 pending），也不会占用恢复令牌。
     *
     * @param request   审批内容（run / 会话 / 应用 / 工具与入参 / 风险等级）
     * @param approved  用户是否放行
     * @param decidedBy 决策人；匿名网页路径没有登录态，由调用方给可辨识的占位标识
     * @param remark    决策备注（确认卡片文案或拒绝原因）
     */
    void recordDecided(ApprovalRequest request, boolean approved, String decidedBy, String remark);
}
