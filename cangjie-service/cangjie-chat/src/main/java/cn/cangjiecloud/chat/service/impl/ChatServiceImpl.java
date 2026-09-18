package cn.cangjiecloud.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.harness.ChatHarnessContextFactory;
import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.chat.harness.SseHarnessListener;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.core.harness.AgentHarness;
import cn.cangjiecloud.core.harness.HarnessListener;
import cn.cangjiecloud.core.harness.HarnessOutcome;
import cn.cangjiecloud.core.harness.HarnessRequest;
import cn.cangjiecloud.core.harness.HarnessTimeoutException;
import cn.cangjiecloud.core.harness.RunStatus;
import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.chat.service.LongTermMemoryExtractService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.harness.context.ContextResult;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.entity.WorkflowExecutionEntity;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IModelService modelService;
    private final LongTermMemoryExtractService longTermMemoryExtractService;
    private final ILlmTraceRecorder llmTraceRecorder;
    private final IWorkflowService workflowService;
    private final cn.cangjiecloud.chat.ratelimit.ChatRateLimiter chatRateLimiter;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    private final cn.cangjiecloud.chat.service.SessionSummaryService sessionSummaryService;

    // ==================== 上下文装配管线 ====================
    // 「如何拼一份会话消息」全部收敛到 ContextPipeline：贡献者按槽位产出片段，
    // 由 ContextBudget 统一裁剪；对话侧只消费装配结果。

    private final cn.cangjiecloud.chat.context.ContextRequestFactory contextRequestFactory;
    private final cn.cangjiecloud.core.harness.context.ContextPipeline contextPipeline;

    // ==================== Agent Harness ====================
    // 引擎是对话的唯一执行路径，轮次控制、总超时、工具权限与人工审批都在引擎内完成。

    private final AgentHarness agentHarness;
    private final HarnessConfigResolver harnessConfigResolver;
    private final ChatHarnessContextFactory harnessContextFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResponseDTO chat(ChatRequestDTO request) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        ApplicationEntity application = getApplication(request.getApplicationId());
        checkChatAccess(application);
        ChatSessionEntity session = getOrCreateSession(request, application);

        // 设置链路上下文，供 LlmTraceRecorder 及其他下游自动获取
        TraceContext.setTraceId(traceId);
        TraceContext.setSessionId(session.getSessionId());
        TraceContext.setUserId(UserContext.getUserId());
        List<Map<String, Object>> retrievalSources = new ArrayList<>();

        // === 工作流路由 ===
        // 如果应用类型是 workflow，走工作流执行
        if ("workflow".equals(application.getType())) {
            return routeToWorkflow(application, session, request, retrievalSources, UserContext.getUserId());
        }

        // 上下文装配（须在保存当前用户消息之前：历史片段不含当前输入）
        ContextResult context = contextPipeline.assemble(contextRequestFactory.newRequest(
                application, session.getSessionId(), UserContext.getUserId(), request.getMessage(),
                request.getMessages(), retrievalSources, traceId));

        // 保存用户消息到数据库
        ChatMessageEntity userMessage = saveUserMessage(session, application, request.getMessage());

        // 调用模型（同步）
        ChatResponse chatResponse = callModel(application, session.getSessionId(),
                UserContext.getUserId(), context, traceId);

        long duration = System.currentTimeMillis() - start;

        // 保存 AI 回复并更新统计
        ChatMessageEntity aiMessage = saveAiMessage(session, application, chatResponse, retrievalSources, duration);
        updateSessionStats(session, chatResponse);
        addApplicationTokens(application.getId(), chatResponse.getTotalTokens());
        sessionSummaryService.maybeSummarizeAsync(session.getSessionId(), application.getModelId());

        // 异步触发长期记忆提取（仅应用启用记忆开关时）
        if (Boolean.TRUE.equals(application.getMemoryEnabled())) {
            longTermMemoryExtractService.extract(
                    UserContext.getUserId(), application, session.getSessionId(), userMessage, aiMessage);
        }

        // 记录追踪
        recordTrace("chat", "send", traceId, duration, "success",
                "应用: " + application.getName() + ", 总tokens: " + chatResponse.getTotalTokens());

        log.info("对话完成: app={}, session={}, duration={}ms, tokens={}",
                application.getName(), session.getSessionId(), duration, chatResponse.getTotalTokens());

        return ChatResponseDTO.builder()
                .sessionId(session.getSessionId())
                .message(chatResponse.getContent())
                .role("assistant")
                .retrievalSources(retrievalSources)
                .tokens(chatResponse.getTotalTokens())
                .promptTokens(chatResponse.getPromptTokens())
                .completionTokens(chatResponse.getCompletionTokens())
                .duration(duration)
                .build();
    }

    @Override
    public void chatStream(ChatRequestDTO request, SseEmitter emitter, boolean openAiFormat, String userId) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long chatStart = System.currentTimeMillis();

        // 设置链路上下文
        TraceContext.setTraceId(traceId);
        TraceContext.setUserId(userId);

        // 监听 SSE 生命周期：客户端断开或超时时置位取消信号，终止后续循环与流式读取
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(t -> cancelled.set(true));

        try {
            ApplicationEntity application = getApplication(request.getApplicationId());
            checkChatAccess(application);
            ChatSessionEntity session = getOrCreateSession(request, application);
            TraceContext.setSessionId(session.getSessionId());
            List<Map<String, Object>> retrievalSources = new ArrayList<>();

            // === 工作流路由 ===
            if ("workflow".equals(application.getType())) {
                ChatResponseDTO wfResult = routeToWorkflow(application, session, request, retrievalSources, userId);
                // 以 SSE 形式推送工作流结果
                try {
                    if (openAiFormat) {
                        String json = buildOpenAiChunk("chatcmpl-" + traceId,
                                getModelName(application.getModelId()), System.currentTimeMillis() / 1000,
                                wfResult.getMessage(), "stop");
                        emitter.send(SseEmitter.event().name("message").data(json));
                        emitter.send(SseEmitter.event().name("message").data("[DONE]"));
                    } else {
                        emitter.send(SseEmitter.event().name("init")
                                .data(Map.of("sessionId", session.getSessionId(), "sources", retrievalSources)));
                        emitter.send(SseEmitter.event().name("message")
                                .data(Map.of("delta", wfResult.getMessage(), "done", true)));
                        emitter.send(SseEmitter.event().name("done")
                                .data(Map.of("delta", "", "done", true, "finishReason", "stop")));
                    }
                } catch (IOException e) {
                    log.warn("工作流 SSE 推送失败: {}", e.getMessage());
                }
                return;
            }

            // 上下文装配（须在保存当前用户消息之前：历史片段不含当前输入）
            ContextResult context = contextPipeline.assemble(contextRequestFactory.newRequest(
                    application, session.getSessionId(), userId, request.getMessage(),
                    request.getMessages(), retrievalSources, traceId));

            // 保存用户消息到数据库
            ChatMessageEntity userMessage = saveUserMessage(session, application, request.getMessage());

            // 调用模型（流式，支持多轮 Function Calling）
            long llmStart = System.currentTimeMillis();
            String modelName = getModelName(application.getModelId());
            String requestId = "chatcmpl-" + traceId;
            long created = System.currentTimeMillis() / 1000;

            chatStreamViaHarness(application, session, userMessage, context, retrievalSources,
                    emitter, openAiFormat, requestId, modelName, created, traceId, userId, chatStart,
                    llmStart, cancelled);

        } catch (Exception e) {
            log.error("流式对话初始化失败: traceId={}", traceId, e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", e.getMessage())));
            } catch (IOException ex) {
                log.warn("SSE 错误推送失败: {}", ex.getMessage());
            }
        } finally {
            emitter.complete();
        }
    }

    private String buildOpenAiChunk(String id, String model, long created, String delta, String finishReason) {
        return String.format(
                "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":%s}]}",
                id, created, model,
                delta.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"),
                finishReason == null ? "null" : "\"" + finishReason + "\"");
    }

    /**
     * 对话准入校验：请求限流 + token 配额
     */
    private void checkChatAccess(ApplicationEntity application) {
        chatRateLimiter.checkRateLimit(application.getId());
        Long quota = application.getTokenQuota();
        if (quota != null && quota > 0) {
            long used = application.getTokensUsed() != null ? application.getTokensUsed() : 0L;
            if (used >= quota) {
                throw new ApiException("应用 token 配额已用尽，请联系管理员提升配额");
            }
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

    private ApplicationEntity getApplication(String applicationId) {
        ApplicationEntity application = applicationService.getById(applicationId);
        if (application == null) {
            throw new ApiException("应用不存在: " + applicationId);
        }
        if (!"published".equals(application.getStatus())) {
            throw new ApiException("应用未发布，无法对话");
        }
        return application;
    }

    private ChatSessionEntity getOrCreateSession(ChatRequestDTO request, ApplicationEntity application) {
        if (StringUtils.hasText(request.getSessionId())) {
            ChatSessionEntity existing = chatSessionService.getBySessionId(request.getSessionId());
            if (existing != null) {
                return existing;
            }
        }
        ChatSessionEntity session = new ChatSessionEntity();
        session.setApplicationId(application.getId());
        session.setSessionId(StringUtils.hasText(request.getSessionId())
                ? request.getSessionId() : IdUtil.fastSimpleUUID());
        session.setTitle(generateTitle(request.getMessage()));
        session.setSource(StringUtils.hasText(request.getSource()) ? request.getSource() : "api");
        session.setModelId(application.getModelId());
        session.setStatus("active");
        session.setMessageCount(0);
        session.setTokensUsed(0);
        session.setUserId(StringUtils.hasText(request.getUserId()) ? request.getUserId() : null);
        chatSessionService.save(session);
        return session;
    }

    private ChatMessageEntity saveUserMessage(ChatSessionEntity session,
                                               ApplicationEntity application,
                                               String content) {
        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setSessionId(session.getSessionId());
        userMessage.setApplicationId(application.getId());
        userMessage.setRole("user");
        userMessage.setContent(content);
        chatMessageService.save(userMessage);
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        return userMessage;
    }

    private ChatMessageEntity saveAiMessage(ChatSessionEntity session,
                                            ApplicationEntity application,
                                            ChatResponse chatResponse,
                                            List<Map<String, Object>> retrievalSources,
                                            long duration) {
        ChatMessageEntity aiMessage = new ChatMessageEntity();
        aiMessage.setSessionId(session.getSessionId());
        aiMessage.setApplicationId(application.getId());
        aiMessage.setRole("assistant");
        aiMessage.setContent(chatResponse.getContent());
        aiMessage.setTokens(chatResponse.getTotalTokens());
        aiMessage.setRetrievalSources(retrievalSources.isEmpty() ? null : JSON.toJSONString(retrievalSources));
        aiMessage.setDuration(duration);
        chatMessageService.save(aiMessage);
        return aiMessage;
    }

    private void updateSessionStats(ChatSessionEntity session, ChatResponse chatResponse) {
        session.setMessageCount(session.getMessageCount() + 1);
        session.setTokensUsed((session.getTokensUsed() == null ? 0 : session.getTokensUsed())
                + (chatResponse.getTotalTokens() > 0 ? chatResponse.getTotalTokens() : 0));
        chatSessionService.updateById(session);
    }

    private void updateSessionStats(ChatSessionEntity session, ChatMessageEntity aiMessage) {
        session.setMessageCount(session.getMessageCount() + 1);
        session.setTokensUsed((session.getTokensUsed() == null ? 0 : session.getTokensUsed())
                + (aiMessage.getTokens() > 0 ? aiMessage.getTokens() : 0));
        chatSessionService.updateById(session);
    }

    /**
     * 同步对话：轮次控制、总超时与工具审批由 Agent Harness 引擎统一处理；超时不写 llm_trace
     * 失败行、其余失败写失败行，两者都以 {@link ApiException} 抛出。
     */
    private ChatResponse callModel(ApplicationEntity application, String sessionId, String userId,
                                   ContextResult context, String traceId) {
        String modelName = getModelName(application.getModelId());
        long llmStart = System.currentTimeMillis();

        HarnessRequest request = harnessContextFactory.newRequest(application, context, false, null,
                sessionId, userId, traceId);
        HarnessOutcome outcome = agentHarness.run(request, HarnessListener.NOOP);
        List<ChatMessage> conversation = outcome.getConversation();

        if (outcome.getStatus() == RunStatus.WAITING_APPROVAL) {
            throw new ApiException("存在待人工审批的工具调用，请通过 POST /api/chat/approval/"
                    + (outcome.getPendingApproval() == null ? "{approvalId}" : outcome.getPendingApproval().getApprovalId())
                    + "/decide 恢复运行: runId=" + outcome.getRunId());
        }
        if (outcome.getStatus() == RunStatus.WAITING_LOCAL) {
            throw new ApiException("存在需在浏览器侧执行的本地工具调用，本地工具仅支持网页流式对话: runId="
                    + outcome.getRunId());
        }
        if (outcome.getStatus() != RunStatus.COMPLETED) {
            String error = outcome.getErrorMessage() == null ? "模型调用失败" : outcome.getErrorMessage();
            boolean timeout = HarnessTimeoutException.isTimeoutMessage(error);
            if (traceCollector != null) {
                recordTrace("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "fail",
                        timeout ? "Agent 循环总超时（>" + request.getLoopPolicy().getTimeoutSeconds() + "s），已终止"
                                : "模型调用失败: " + error);
            }
            if (!timeout) {
                llmTraceRecorder.recordFailure(ILlmTraceRecorder.LlmTraceRecord.builder()
                        .requestId("chatcmpl-" + traceId)
                        .appId(application.getId())
                        .appName(application.getName())
                        .modelId(application.getModelId())
                        .modelName(modelName)
                        .promptContent(JSON.toJSONString(conversation))
                        .duration(System.currentTimeMillis() - llmStart)
                        .startTimeMs(llmStart)
                        .build(), "模型调用失败: " + error);
            }
            throw new ApiException(timeout ? error : "模型调用失败: " + error);
        }

        ChatResponse response = ChatResponse.builder()
                .content(outcome.getFinalText())
                .finishReason(outcome.getFinishReason())
                .promptTokens((int) outcome.getInputTokens())
                .completionTokens((int) outcome.getOutputTokens())
                .totalTokens((int) outcome.getTotalTokens())
                .build();

        if (traceCollector != null) {
            recordTrace("chat", "llm_call", traceId,
                    System.currentTimeMillis() - llmStart, "success",
                    "模型: " + modelName + ", tokens: " + response.getTotalTokens());
        }
        llmTraceRecorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                .requestId("chatcmpl-" + traceId)
                .appId(application.getId())
                .appName(application.getName())
                .modelId(application.getModelId())
                .modelName(modelName)
                .promptContent(JSON.toJSONString(conversation))
                .inputTokens(outcome.getInputTokens())
                .outputTokens(outcome.getOutputTokens())
                .totalTokens(outcome.getTotalTokens())
                .responseContent(outcome.getFinalText())
                .finishReason(outcome.getFinishReason())
                .duration(System.currentTimeMillis() - llmStart)
                .startTimeMs(llmStart)
                .build());
        return response;
    }

    /**
     * 流式对话：SSE 帧由 {@link SseHarnessListener} 逐帧推送——失败只记 trace 不落库，
     * 成功先落库再发终止帧，等待审批时不发终止帧（由恢复运行接口继续产出）。
     */
    private void chatStreamViaHarness(ApplicationEntity application, ChatSessionEntity session,
                                      ChatMessageEntity userMessage, ContextResult context,
                                      List<Map<String, Object>> retrievalSources, SseEmitter emitter,
                                      boolean openAiFormat, String requestId, String modelName, long created,
                                      String traceId, String userId, long chatStart, long llmStart,
                                      AtomicBoolean cancelled) {
        SseHarnessListener listener = new SseHarnessListener(emitter, openAiFormat, requestId, modelName, created,
                session.getSessionId(), retrievalSources, harnessConfigResolver.isSseToolEvents());
        listener.sendInit();

        HarnessRequest request = harnessContextFactory.newRequest(application, context, true, cancelled,
                session.getSessionId(), userId, traceId);
        HarnessOutcome outcome = agentHarness.run(request, listener);

        if (outcome.getStatus() == RunStatus.WAITING_APPROVAL) {
            log.info("流式对话挂起等待审批: runId={}, session={}, tool={}", outcome.getRunId(),
                    session.getSessionId(),
                    outcome.getPendingApproval() == null ? null : outcome.getPendingApproval().getToolName());
            // approval_required 已推送，产出改由 decide 接口同步返回：立即结束本次流，
            // 否则连接要挂到 SSE 超时才释放
            emitter.complete();
            return;
        }

        if (outcome.getStatus() == RunStatus.WAITING_LOCAL) {
            log.info("流式对话挂起等待本地工具执行: runId={}, session={}, tool={}", outcome.getRunId(),
                    session.getSessionId(),
                    outcome.getPendingLocalTool() == null ? null : outcome.getPendingLocalTool().getToolName());
            // local_tool_required 已推送，浏览器执行完改由结果回传接口同步返回产出：立即结束本次流
            emitter.complete();
            return;
        }

        List<ChatMessage> messages = outcome.getConversation();
        if (outcome.getStatus() != RunStatus.COMPLETED) {
            String error = outcome.getErrorMessage() == null ? "对话处理失败" : outcome.getErrorMessage();
            // 流式中断前可能已吐出部分正文：保留落库，页面与历史记录都能看到已产出的内容
            String partial = outcome.getFinalText();
            if (partial != null && !partial.isBlank()) {
                long partialDuration = System.currentTimeMillis() - chatStart;
                ChatMessageEntity partialMessage = saveAiMessage(session, application,
                        ChatResponse.builder()
                                .content(partial)
                                .totalTokens((int) outcome.getTotalTokens())
                                .finishReason(null)
                                .build(),
                        retrievalSources, partialDuration);
                updateSessionStats(session, partialMessage);
                addApplicationTokens(application.getId(), outcome.getTotalTokens());
            }
            // 错误帧已由 listener 推送；记录失败 trace
            if (traceCollector != null) {
                recordTrace("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "fail", "模型调用失败: " + error);
            }
            llmTraceRecorder.recordFailure(ILlmTraceRecorder.LlmTraceRecord.builder()
                    .requestId(requestId)
                    .appId(application.getId())
                    .appName(application.getName())
                    .modelId(application.getModelId())
                    .modelName(modelName)
                    .promptContent(JSON.toJSONString(messages))
                    .responseContent(partial)
                    .duration(System.currentTimeMillis() - llmStart)
                    .startTimeMs(llmStart)
                    .build(), error);
            return;
        }

        if (traceCollector != null) {
            recordTrace("chat", "llm_call", traceId,
                    System.currentTimeMillis() - llmStart, "success",
                    "模型: " + modelName + ", 流式推送完成");
        }

        long duration = System.currentTimeMillis() - chatStart;
        String finalText = outcome.getFinalText() == null ? "" : outcome.getFinalText();
        ChatMessageEntity aiMessage = saveAiMessage(session, application,
                ChatResponse.builder()
                        .content(finalText)
                        .totalTokens((int) outcome.getTotalTokens())
                        .finishReason(null)
                        .build(),
                retrievalSources, duration);

        llmTraceRecorder.recordSuccess(ILlmTraceRecorder.LlmTraceRecord.builder()
                .requestId(requestId)
                .appId(application.getId())
                .appName(application.getName())
                .modelId(application.getModelId())
                .modelName(modelName)
                .promptContent(JSON.toJSONString(messages))
                .inputTokens(outcome.getInputTokens())
                .outputTokens(outcome.getOutputTokens())
                .totalTokens(outcome.getTotalTokens())
                .responseContent(finalText)
                .finishReason(outcome.getFinishReason())
                .duration(System.currentTimeMillis() - llmStart)
                .startTimeMs(llmStart)
                .build());

        updateSessionStats(session, aiMessage);
        addApplicationTokens(application.getId(), outcome.getTotalTokens());
        sessionSummaryService.maybeSummarizeAsync(session.getSessionId(), application.getModelId());

        if (Boolean.TRUE.equals(application.getMemoryEnabled())) {
            longTermMemoryExtractService.extract(
                    userId, application, session.getSessionId(), userMessage, aiMessage);
        }

        listener.sendTerminal();
    }

    private String getModelName(String modelId) {
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

    private String generateTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return "新对话";
        }
        return message.length() > 30 ? message.substring(0, 30) + "..." : message;
    }

    private void recordTrace(String module, String operation, String traceId, long duration, String status, String detail) {
        if (traceCollector != null) {
            traceCollector.record(module, operation, traceId, duration, status, detail);
        }
    }

    // ========== Tool / Workflow Integration ==========

    /**
     * 路由到工作流执行
     * <p>
     * 对于 type = "workflow" 的应用，通过工作流引擎完成整个对话流程
     */
    private ChatResponseDTO routeToWorkflow(ApplicationEntity application, ChatSessionEntity session,
                                            ChatRequestDTO request,
                                            List<Map<String, Object>> retrievalSources,
                                            String userId) {
        // 查找关联工作流
        WorkflowEntity wf = workflowService.getByApplicationId(application.getId());
        if (wf == null) {
            throw new ApiException("应用关联的工作流不存在");
        }

        // 保存用户消息
        ChatMessageEntity userMessage = saveUserMessage(session, application, request.getMessage());

        // 构建工作流输入
        Map<String, Object> wfInputs = new HashMap<>();
        wfInputs.put("message", request.getMessage());
        wfInputs.put("userId", userId);
        wfInputs.put("applicationId", application.getId());
        wfInputs.put("sessionId", session.getSessionId());

        long start = System.currentTimeMillis();
        WorkflowExecutionEntity execution = workflowService.execute(wf.getId(), wfInputs);
        long duration = System.currentTimeMillis() - start;

        // 从工作流输出中提取最终结果
        String resultContent = execution.getOutputs() != null ? execution.getOutputs() : "";
        if (!"completed".equals(execution.getStatus())) {
            resultContent = "工作流执行失败: " + (execution.getErrorMessage() != null ? execution.getErrorMessage() : "未知错误");
        }

        // 保存 AI 回复
        ChatResponse chatResponse = ChatResponse.builder()
                .content(resultContent)
                .totalTokens(0)
                .build();
        ChatMessageEntity aiMessage = saveAiMessage(session, application, chatResponse, retrievalSources, duration);
        updateSessionStats(session, aiMessage);

        log.info("工作流执行完成: app={}, workflow={}, status={}, duration={}ms",
                application.getName(), wf.getName(), execution.getStatus(), duration);

        return ChatResponseDTO.builder()
                .sessionId(session.getSessionId())
                .message(resultContent)
                .role("assistant")
                .retrievalSources(retrievalSources)
                .tokens(0)
                .promptTokens(0)
                .completionTokens(0)
                .duration(duration)
                .build();
    }
}