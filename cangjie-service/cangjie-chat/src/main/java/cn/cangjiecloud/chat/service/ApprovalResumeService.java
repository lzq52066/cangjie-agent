package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.ApprovalDecisionDTO;
import cn.cangjiecloud.application.api.dto.ApprovalResumeDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审批决策与断点续跑。
 * <p>
 * 引擎在 {@code DefaultAgentHarness#suspend} 里写下检查点（agent_run.context_snapshot + 一次性
 * resumeToken）后以 {@code waiting_approval} 结束请求，不占用线程等待人工。人工结论必须由这里送回：
 * 校验令牌 → 落库决策 → {@link AgentHarness#resume} 重建上下文继续跑完剩下的工具与轮次。
 * <p>
 * 顺序不可调换：必须先以 CAS 写决策（审批单只允许从 pending 迁出一次），再消费检查点上的令牌。
 * 反过来会让并发重复请求拿到同一份检查点，导致工具被执行两次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalResumeService {

    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final IAgentApprovalService approvalStore;
    private final AgentHarness agentHarness;
    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final SessionSummaryService sessionSummaryService;

    /**
     * 读取可决策的审批单（控制器据此拿到归属应用再做 API Key 校验）
     */
    public ApprovalRequest requireDecidable(String approvalId) {
        ApprovalRequest approval = approvalStore.find(approvalId);
        if (approval == null) {
            throw new ApiException("审批单不存在: " + approvalId);
        }
        if (!StringUtils.hasText(approval.getRunId()) || !StringUtils.hasText(approval.getResumeToken())) {
            throw new ApiException("审批单缺少运行标识或恢复令牌，无法恢复运行");
        }
        if (approval.getExpireAt() > 0 && approval.getExpireAt() < System.currentTimeMillis()) {
            throw new ApiException("审批单已超时，本次运行不再恢复，请重新发起对话");
        }
        return approval;
    }

    /**
     * 会话下待审批单（前端刷新 / 切换会话后恢复卡片用）
     */
    public ApprovalResumeDTO.PendingApproval findPending(String sessionId) {
        return toPending(approvalStore.findPendingBySession(sessionId));
    }

    /**
     * 提交决策并恢复运行，返回恢复后的产出
     */
    public ApprovalResumeDTO decide(ApprovalRequest approval, ApprovalDecisionDTO decision) {
        // 匿名网页路径没有凭证，恢复令牌 + 会话归属是仅有的两道校验，两者必须成对
        if (StringUtils.hasText(decision.getSessionId())
                && StringUtils.hasText(approval.getSessionId())
                && !approval.getSessionId().equals(decision.getSessionId())) {
            throw new ApiException("会话与审批单不匹配");
        }
        String decidedBy = StringUtils.hasText(decision.getDecidedBy())
                ? decision.getDecidedBy() : UserContext.getUserId();
        String status = decision.isApproved() ? STATUS_APPROVED : STATUS_REJECTED;
        if (!approvalStore.decide(approval.getApprovalId(), status, decidedBy, decision.getRemark())) {
            throw new ApiException("审批单已处理或已失效，请勿重复提交");
        }
        log.info("审批决策已提交: approvalId={}, runId={}, decision={}, decidedBy={}",
                approval.getApprovalId(), approval.getRunId(), status, decidedBy);

        HarnessOutcome outcome;
        try {
            outcome = agentHarness.resume(approval.getRunId(), decision.isApproved(), decidedBy,
                    decision.getRemark(), approval.getResumeToken(), HarnessListener.NOOP);
        } catch (Exception ex) {
            // 决策已生效但检查点取不回（run 已终结 / 令牌已被消费），保留决策不回收，如实报错
            log.error("审批决策后恢复运行失败: approvalId={}, runId={}",
                    approval.getApprovalId(), approval.getRunId(), ex);
            throw new ApiException("恢复运行失败: " + ex.getMessage());
        }
        return toResult(approval, outcome);
    }

    private ApprovalResumeDTO toResult(ApprovalRequest approval, HarnessOutcome outcome) {
        ApprovalResumeDTO.ApprovalResumeDTOBuilder builder = ApprovalResumeDTO.builder()
                .runId(outcome.getRunId())
                .sessionId(approval.getSessionId())
                .status(outcome.getStatus() == null ? null : outcome.getStatus().value())
                .rounds(outcome.getRounds())
                .toolCallCount(outcome.getToolCallCount())
                .tokens((int) outcome.getTotalTokens())
                .promptTokens((int) outcome.getInputTokens())
                .completionTokens((int) outcome.getOutputTokens())
                .duration(outcome.getDurationMs())
                .finishReason(outcome.getFinishReason());

        if (outcome.getStatus() == RunStatus.WAITING_APPROVAL) {
            // 同轮后续工具又命中审批：把新令牌交回调用方，继续下一轮决策
            builder.pendingApproval(toPending(outcome.getPendingApproval()));
            return builder.build();
        }
        if (outcome.getStatus() != RunStatus.COMPLETED) {
            String error = outcome.getErrorMessage() == null ? "恢复运行失败" : outcome.getErrorMessage();
            builder.errorMessage(error);
            // 模型调用 trace 已由 ChatModelListener 统一记录，这里只留业务侧日志
            log.warn("审批恢复运行未完成: runId={}, status={}, error={}",
                    outcome.getRunId(), outcome.getStatus(), error);
            return builder.build();
        }

        String finalText = outcome.getFinalText() == null ? "" : outcome.getFinalText();
        builder.message(finalText);
        persist(approval, outcome, finalText);
        return builder.build();
    }

    /**
     * 收尾：回答落库为 assistant 消息、会话与应用统计累加。
     * <p>
     * 回答已经在库里（agent_run_step），落库失败只降级为日志，不能把已成功的恢复报成失败。
     */
    private void persist(ApprovalRequest approval, HarnessOutcome outcome, String finalText) {
        ChatSessionEntity session = StringUtils.hasText(approval.getSessionId())
                ? chatSessionService.getBySessionId(approval.getSessionId()) : null;
        ApplicationEntity application = StringUtils.hasText(approval.getApplicationId())
                ? applicationService.getById(approval.getApplicationId()) : null;
        if (session == null || application == null) {
            log.warn("恢复运行的会话或应用不存在，跳过落库: runId={}, sessionId={}, appId={}",
                    outcome.getRunId(), approval.getSessionId(), approval.getApplicationId());
            return;
        }

        ChatMessageEntity aiMessage = new ChatMessageEntity();
        aiMessage.setSessionId(session.getSessionId());
        aiMessage.setApplicationId(application.getId());
        aiMessage.setRole("assistant");
        aiMessage.setContent(finalText);
        aiMessage.setTokens((int) outcome.getTotalTokens());
        aiMessage.setDuration(outcome.getDurationMs());
        chatMessageService.save(aiMessage);

        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        session.setTokensUsed((session.getTokensUsed() == null ? 0 : session.getTokensUsed())
                + (int) outcome.getTotalTokens());
        chatSessionService.updateById(session);

        addApplicationTokens(application.getId(), outcome.getTotalTokens());
        sessionSummaryService.maybeSummarizeAsync(session.getSessionId(), application.getModelId());
    }

    /**
     * 累计应用 token 消耗（SQL 原子自增，避免并发覆盖）
     */
    private void addApplicationTokens(String applicationId, long tokens) {
        if (tokens <= 0) {
            return;
        }
        try {
            applicationService.lambdaUpdate()
                    .eq(ApplicationEntity::getId, applicationId)
                    .setSql("tokens_used = tokens_used + " + tokens)
                    .update();
        } catch (Exception e) {
            log.warn("应用 token 消耗累计失败: appId={}, {}", applicationId, e.getMessage());
        }
    }

    private ApprovalResumeDTO.PendingApproval toPending(ApprovalRequest approval) {
        if (approval == null) {
            return null;
        }
        return ApprovalResumeDTO.PendingApproval.builder()
                .approvalId(approval.getApprovalId())
                .runId(approval.getRunId())
                .toolName(approval.getToolName())
                .toolType(approval.getToolType())
                .arguments(approval.getArguments())
                .reason(approval.getReason())
                .riskLevel(approval.getRiskLevel())
                .expireAt(approval.getExpireAt())
                .resumeToken(approval.getResumeToken())
                .build();
    }
}
