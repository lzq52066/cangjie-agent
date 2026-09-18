package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.application.api.dto.LocalToolResultDTO;
import cn.cangjiecloud.application.api.dto.LocalToolResumeDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.LocalToolCall;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.service.IAgentRunService;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import cn.cangjiecloud.common.exception.ApiException;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 浏览器侧本地工具结果回传与断点续跑。
 * <p>
 * 与 {@link ApprovalResumeService} 同构，区别在于挂起不建审批单、无需人工决策：
 * 校验 run 归属与一次性令牌 → {@link AgentHarness#completeLocalTool} 把浏览器执行结果
 * 塞进检查点上下文并继续跑完剩下的工具与轮次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalToolResumeService {

    private final IAgentRunService agentRunService;
    private final AgentHarness agentHarness;
    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IModelService modelService;
    private final ILlmTraceRecorder llmTraceRecorder;
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
            recordTrace(outcome, null, error);
            return builder.build();
        }

        String finalText = outcome.getFinalText() == null ? "" : outcome.getFinalText();
        builder.message(finalText);
        persist(outcome, finalText);
        return builder.build();
    }

    /**
     * 收尾：回答落库为 assistant 消息、会话与应用统计累加、模型 trace 记录。
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
            recordTrace(outcome, finalText, null);
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
        recordTrace(outcome, finalText, null);
        sessionSummaryService.maybeSummarizeAsync(session.getSessionId(), application.getModelId());
    }

    private void recordTrace(HarnessOutcome outcome, String finalText, String error) {
        try {
            AgentRunEntity run = agentRunService.getById(outcome.getRunId());
            String applicationId = run == null ? null : run.getAppId();
            ApplicationEntity application = applicationId == null ? null
                    : applicationService.getById(applicationId);
            String modelId = application == null ? null : application.getModelId();
            ILlmTraceRecorder.LlmTraceRecord.LlmTraceRecordBuilder record = ILlmTraceRecorder.LlmTraceRecord.builder()
                    .requestId("chatcmpl-" + outcome.getRunId())
                    .appId(applicationId)
                    .appName(application == null ? null : application.getName())
                    .sessionId(run == null ? null : run.getSessionId())
                    .modelId(modelId)
                    .modelName(resolveModelName(modelId))
                    .promptContent(JSON.toJSONString(outcome.getConversation()))
                    .duration(outcome.getDurationMs());
            if (error == null) {
                llmTraceRecorder.recordSuccess(record
                        .inputTokens(outcome.getInputTokens())
                        .outputTokens(outcome.getOutputTokens())
                        .totalTokens(outcome.getTotalTokens())
                        .responseContent(finalText)
                        .finishReason(outcome.getFinishReason())
                        .build());
            } else {
                llmTraceRecorder.recordFailure(record.build(), error);
            }
        } catch (Exception ex) {
            log.warn("恢复运行的 llm_trace 记录失败: runId={}, {}", outcome.getRunId(), ex.getMessage());
        }
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

    private String resolveModelName(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return "默认模型";
        }
        try {
            ModelEntity model = modelService.getById(modelId);
            if (model != null && StringUtils.hasText(model.getName())) {
                return model.getName();
            }
        } catch (Exception e) {
            log.warn("获取模型名称失败: modelId={}, {}", modelId, e.getMessage());
        }
        return modelId;
    }
}
