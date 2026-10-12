package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.LocalToolResultDTO;
import cn.cangjiecloud.application.api.dto.LocalToolResumeDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.ApprovalRequest;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.core.harness.ToolStatus;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import cn.cangjiecloud.observability.service.IAgentRunService;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 浏览器侧本地工具结果回传与断点续跑。
 * <p>
 * 与 {@link ApprovalResumeService} 同构，区别在于闸门在调用方环境：本地工具不进服务端钩子链，
 * 挂起时不建 pending 审批单、无需人工点「同意」，run 等的是浏览器把结果回传（见
 * {@code ToolGatewayImpl#dispatch} 的 LOCAL 短路）。但变更类工具的二次确认同样是人工决策，
 * 由 {@link #recordUserDecision} 在续跑前补记成终态审批单，否则「谁允许删了这个文件」在库里查不到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalToolResumeService {

    /** 匿名网页路径没有登录态，决策人只能记成可辨识的占位标识 */
    private static final String ANONYMOUS_DECIDED_BY = "浏览器用户";

    private final IAgentRunService agentRunService;
    private final IAgentApprovalService approvalStore;
    private final AgentHarness agentHarness;
    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final SessionSummaryService sessionSummaryService;

    /**
     * 校验挂起中的 run（控制器据此拿到归属应用再做发布态校验）
     */
    public AgentRunEntity requireWaitingRun(LocalToolResultDTO body) {
        AgentRunEntity run = agentRunService.getById(body.getRunId());
        if (run == null) {
            throw new ApiException("运行不存在: " + body.getRunId());
        }
        if (!RunStatus.WAITING_LOCAL.value().equals(run.getStatus())) {
            throw new ApiException("运行不在等待本地工具执行的状态，请勿重复提交");
        }
        if (!StringUtils.hasText(run.getResumeToken()) || !run.getResumeToken().equals(body.getResumeToken())) {
            log.warn("本地工具恢复令牌校验失败: runId={}", body.getRunId());
            throw new ApiException("恢复令牌无效或已失效");
        }
        // 匿名网页路径没有凭证，恢复令牌 + 会话归属是仅有的两道校验，两者必须成对
        if (StringUtils.hasText(body.getSessionId())
                && StringUtils.hasText(run.getSessionId())
                && !run.getSessionId().equals(body.getSessionId())) {
            throw new ApiException("会话与运行不匹配");
        }
        return run;
    }

    /**
     * 回传本地执行结果并恢复运行，返回恢复后的产出
     */
    public LocalToolResumeDTO complete(LocalToolResultDTO body) {
        // 决策先落账再续跑：恢复失败时用户确实点过允许/拒绝，这条审计不能跟着一起丢
        recordUserDecision(body);
        String resultJson = body.isFailed() ? body.getErrorMessage() : body.getResult();
        HarnessOutcome outcome;
        try {
            outcome = agentHarness.completeLocalTool(body.getRunId(), body.getCallId(),
                    resultJson, body.isFailed(), body.getResumeToken(), HarnessListener.NOOP);
        } catch (Exception ex) {
            log.error("本地工具结果回传后恢复运行失败: runId={}, callId={}", body.getRunId(), body.getCallId(), ex);
            throw new ApiException("恢复运行失败: " + ex.getMessage());
        }
        // 再次挂起时会话标识随 LocalToolCall 下发；终态则从 run 记录补取
        String sessionId = outcome.getPendingLocalTool() == null
                ? lookupSessionId(outcome.getRunId()) : outcome.getPendingLocalTool().getSessionId();
        return toResult(outcome, sessionId);
    }

    private String lookupSessionId(String runId) {
        AgentRunEntity run = agentRunService.getById(runId);
        return run == null ? null : run.getSessionId();
    }

    /**
     * 把浏览器二次确认的允许/拒绝补记成一张终态审批单。
     * <p>
     * 只读工具（列目录/读取）自动执行、没有人参与，{@code confirmed} 为空时不留痕，
     * 否则审批单会被无意义的自动执行灌满。工具名与入参取挂起时已落的步骤记录，
     * 不采信客户端回传的字符串——留痕要能回答「到底执行了什么」。
     */
    private void recordUserDecision(LocalToolResultDTO body) {
        if (body.getConfirmed() == null) {
            return;
        }
        AgentRunEntity run = agentRunService.getById(body.getRunId());
        AgentRunStepEntity call = lastWaitingLocalStep(body.getRunId());
        String userId = run == null ? null : run.getUserId();
        String riskLevel = StringUtils.hasText(body.getRiskLevel()) ? body.getRiskLevel() : "medium";
        approvalStore.recordDecided(ApprovalRequest.builder()
                .runId(body.getRunId())
                .stepId(call == null ? null : call.getId())
                .sessionId(run == null ? body.getSessionId() : run.getSessionId())
                .applicationId(run == null ? null : run.getAppId())
                .userId(userId)
                .toolName(call == null ? body.getToolName() : call.getName())
                .toolType(ToolConstants.ToolType.LOCAL)
                .arguments(call == null ? null : call.getInput())
                .reason("浏览器侧本地工具二次确认（风险等级：" + riskLevel + "）")
                .riskLevel(riskLevel)
                .build(),
                Boolean.TRUE.equals(body.getConfirmed()),
                StringUtils.hasText(userId) ? userId : ANONYMOUS_DECIDED_BY,
                decisionRemark(body));
    }

    /** 确认卡片写了什么就记什么；没有卡片文案时退回拒绝原因 */
    private String decisionRemark(LocalToolResultDTO body) {
        if (StringUtils.hasText(body.getActionSummary())) {
            return body.getActionSummary();
        }
        return StringUtils.hasText(body.getErrorMessage()) ? body.getErrorMessage() : null;
    }

    /** 本地工具挂起前网关已落 waiting_local 步骤，同一次 run 里取最后一条即当前待回传调用 */
    private AgentRunStepEntity lastWaitingLocalStep(String runId) {
        AgentRunStepEntity matched = null;
        for (AgentRunStepEntity step : agentRunService.listSteps(runId)) {
            if ("tool".equals(step.getType())
                    && ToolStatus.WAITING_LOCAL.value().equals(step.getStatus())) {
                matched = step;
            }
        }
        return matched;
    }

    private LocalToolResumeDTO toResult(HarnessOutcome outcome, String sessionId) {
        LocalToolResumeDTO.LocalToolResumeDTOBuilder builder = LocalToolResumeDTO.builder()
                .runId(outcome.getRunId())
                .sessionId(sessionId)
                .status(outcome.getStatus() == null ? null : outcome.getStatus().value())
                .rounds(outcome.getRounds())
                .toolCallCount(outcome.getToolCallCount())
                .tokens((int) outcome.getTotalTokens())
                .promptTokens((int) outcome.getInputTokens())
                .completionTokens((int) outcome.getOutputTokens())
                .duration(outcome.getDurationMs())
                .finishReason(outcome.getFinishReason());

        if (outcome.getStatus() == RunStatus.WAITING_LOCAL) {
            builder.pendingLocalTool(toPending(outcome.getPendingLocalTool()));
            return builder.build();
        }
        if (outcome.getStatus() == RunStatus.WAITING_APPROVAL) {
            builder.pendingApproval(toPendingApproval(outcome));
            return builder.build();
        }
        if (outcome.getStatus() != RunStatus.COMPLETED) {
            String error = outcome.getErrorMessage() == null ? "恢复运行失败" : outcome.getErrorMessage();
            builder.errorMessage(error);
            // 模型调用 trace 已由 ChatModelListener 统一记录，这里只留业务侧日志
            log.warn("本地工具恢复运行未完成: runId={}, status={}, error={}",
                    outcome.getRunId(), outcome.getStatus(), error);
            return builder.build();
        }

        String finalText = outcome.getFinalText() == null ? "" : outcome.getFinalText();
        builder.message(finalText);
        persist(outcome, finalText);
        return builder.build();
    }

    /**
     * 收尾：回答落库为 assistant 消息、会话与应用统计累加。
     */
    private void persist(HarnessOutcome outcome, String finalText) {
        AgentRunEntity run = agentRunService.getById(outcome.getRunId());
        String sessionId = run == null ? null : run.getSessionId();
        String applicationId = run == null ? null : run.getAppId();
        ChatSessionEntity session = StringUtils.hasText(sessionId)
                ? chatSessionService.getBySessionId(sessionId) : null;
        ApplicationEntity application = StringUtils.hasText(applicationId)
                ? applicationService.getById(applicationId) : null;
        if (session == null || application == null) {
            log.warn("恢复运行的会话或应用不存在，跳过落库: runId={}, sessionId={}, appId={}",
                    outcome.getRunId(), sessionId, applicationId);
            return;
        }
        // 会话归属以 run 记录为准，回填给 DTO 供前端关联
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

    private LocalToolResumeDTO.PendingLocalTool toPending(LocalToolCall call) {
        if (call == null) {
            return null;
        }
        return LocalToolResumeDTO.PendingLocalTool.builder()
                .runId(call.getRunId())
                .callId(call.getCallId())
                .toolName(call.getToolName())
                .arguments(call.getArguments())
                .resumeToken(call.getResumeToken())
                .build();
    }

    private cn.cangjiecloud.application.api.dto.ApprovalResumeDTO.PendingApproval toPendingApproval(
            HarnessOutcome outcome) {
        if (outcome.getPendingApproval() == null) {
            return null;
        }
        var approval = outcome.getPendingApproval();
        return cn.cangjiecloud.application.api.dto.ApprovalResumeDTO.PendingApproval.builder()
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
