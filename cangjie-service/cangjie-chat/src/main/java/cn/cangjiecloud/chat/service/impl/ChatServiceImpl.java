package cn.cangjiecloud.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.cangjiecloud.application.api.dto.ChatRequestDTO;
import cn.cangjiecloud.application.api.dto.ChatResponseDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.chat.service.IChatService;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.chat.service.LongTermMemoryExtractService;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatChunk;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final IModelService modelService;
    private final HybridRetriever hybridRetriever;
    private final IPromptTemplateService promptTemplateService;
    private final ILongTermMemoryService longTermMemoryService;
    private final LongTermMemoryExtractService longTermMemoryExtractService;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    private static final int DEFAULT_TOP_K = 5;
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResponseDTO chat(ChatRequestDTO request) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        ApplicationEntity application = getApplication(request.getApplicationId());
        ChatSessionEntity session = getOrCreateSession(request, application);
        List<Map<String, Object>> retrievalSources = new ArrayList<>();

        // 构建上下文（历史消息）
        Context context = buildContext(application, session.getSessionId(), request.getMessage(),
                retrievalSources, traceId, request.getMessages());

        // 注入长期记忆（如有）
        List<ChatMessage> messages = new ArrayList<>(context.messages());
        injectLongTermMemory(request, messages);

        // 保存用户消息到数据库
        ChatMessageEntity userMessage = saveUserMessage(session, application, request.getMessage());

        // 调用模型（同步）
        ChatResponse chatResponse = callModel(application, messages, traceId);

        long duration = System.currentTimeMillis() - start;

        // 保存 AI 回复并更新统计
        ChatMessageEntity aiMessage = saveAiMessage(session, application, chatResponse, retrievalSources, duration);
        updateSessionStats(session, chatResponse);

        // 异步触发长期记忆提取
        longTermMemoryExtractService.extract(
                UserContext.getUserId(), application, userMessage, aiMessage);

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

        try {
            ApplicationEntity application = getApplication(request.getApplicationId());
            ChatSessionEntity session = getOrCreateSession(request, application);
            List<Map<String, Object>> retrievalSources = new ArrayList<>();

            // 构建上下文（历史消息）
            Context context = buildContext(application, session.getSessionId(), request.getMessage(),
                    retrievalSources, traceId, request.getMessages());

            // 注入长期记忆（如有）
            List<ChatMessage> messages = new ArrayList<>(context.messages());
            injectLongTermMemory(request, messages, userId);

            // 保存用户消息到数据库
            ChatMessageEntity userMessage = saveUserMessage(session, application, request.getMessage());

            // 调用模型（流式）
            long llmStart = System.currentTimeMillis();
            Stream<ChatChunk> chunkStream = callModelStream(application, messages, traceId);

            String modelName = getModelName(application.getModelId());
            String requestId = "chatcmpl-" + traceId;
            long created = System.currentTimeMillis() / 1000;

            StringBuilder fullContent = new StringBuilder();

            if (openAiFormat) {
                // OpenAI 兼容 SSE 格式
                try {
                    chunkStream.forEach(chunk -> {
                        if (chunk.getDelta() != null) {
                            fullContent.append(chunk.getDelta());
                            try {
                                String finishReason = Boolean.TRUE.equals(chunk.isDone()) ? "stop" : null;
                                String json = buildOpenAiChunk(requestId, modelName, created, chunk.getDelta(), finishReason);
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(json));
                            } catch (IOException e) {
                                log.warn("OpenAI SSE 推送失败: {}", e.getMessage());
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("OpenAI 流式对话异常: app={}", application.getName(), e);
                    // 发送 OpenAI 兼容错误事件
                    try {
                        emitter.send(SseEmitter.event().name("message").data(buildOpenAiError(e.getMessage())));
                    } catch (IOException ex) {
                        log.warn("OpenAI SSE 错误推送失败: {}", ex.getMessage());
                    }
                }
                // 发送结束标记
                try {
                    emitter.send(SseEmitter.event().name("message").data("[DONE]"));
                } catch (IOException e) {
                    log.warn("OpenAI SSE [DONE] 推送失败: {}", e.getMessage());
                }
            } else {
                // 内部 SSE 格式
                try {
                    Map<String, Object> initPayload = new HashMap<>();
                    initPayload.put("sessionId", session.getSessionId());
                    initPayload.put("sources", retrievalSources);
                    emitter.send(SseEmitter.event().name("init").data(initPayload));
                } catch (IOException ex) {
                    log.warn("SSE init 事件推送失败: {}", ex.getMessage());
                }
                try {
                    chunkStream.forEach(chunk -> {
                        if (chunk.getError() != null) {
                            // 模型流式调用异常
                            try {
                                Map<String, Object> errorPayload = new HashMap<>();
                                errorPayload.put("delta", "");
                                errorPayload.put("done", true);
                                errorPayload.put("error", chunk.getError());
                                emitter.send(SseEmitter.event().name("error").data(errorPayload));
                            } catch (IOException e) {
                                log.warn("SSE 错误推送失败: {}", e.getMessage());
                            }
                            return;
                        }
                        if (chunk.getDelta() != null) {
                            fullContent.append(chunk.getDelta());
                            try {
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("delta", chunk.getDelta());
                                payload.put("done", Boolean.TRUE.equals(chunk.isDone()));
                                payload.put("finishReason", chunk.getFinishReason());
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(payload));
                            } catch (IOException e) {
                                log.warn("SSE 推送失败: {}", e.getMessage());
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("流式对话异常: app={}", application.getName(), e);
                    try {
                        Map<String, Object> errorPayload = new HashMap<>();
                        errorPayload.put("delta", "");
                        errorPayload.put("done", true);
                        errorPayload.put("error", e.getMessage());
                        emitter.send(SseEmitter.event().name("error").data(errorPayload));
                    } catch (IOException ex) {
                        log.warn("SSE 错误推送失败: {}", ex.getMessage());
                    }
                }
            }

            if (traceCollector != null) {
                recordTrace("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "success",
                        "模型: " + modelName + ", 流式推送完成");
            }

            // 持久化 AI 回复
            long duration = System.currentTimeMillis() - chatStart;
            ChatMessageEntity aiMessage = saveAiMessage(session, application,
                    ChatResponse.builder()
                            .content(fullContent.toString())
                            .totalTokens(0)
                            .finishReason(null)
                            .build(),
                    retrievalSources, duration);

            updateSessionStats(session, aiMessage);

            // 异步触发长期记忆提取
            longTermMemoryExtractService.extract(
                    userId, application, userMessage, aiMessage);

            // 非 OpenAI 格式下发送完成事件
            if (!openAiFormat) {
                try {
                    Map<String, Object> donePayload = new HashMap<>();
                    donePayload.put("delta", "");
                    donePayload.put("done", true);
                    donePayload.put("finishReason", "stop");
                    emitter.send(SseEmitter.event().name("done").data(donePayload));
                } catch (IOException e) {
                    log.warn("SSE done 事件推送失败: {}", e.getMessage());
                }
            }

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

    private String buildOpenAiError(String message) {
        String safe = message == null ? "internal_error"
                : message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"error\":{\"message\":\"" + safe + "\",\"type\":\"internal_error\"}}";
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

    private Context buildContext(ApplicationEntity application, String sessionId, String userMessage,
                                 List<Map<String, Object>> retrievalSources, String traceId,
                                 List<ChatRequestDTO.ConversationMessage> requestMessages) {
        StringBuilder systemPrompt = new StringBuilder();

        // 提示词模板
        if (StringUtils.hasText(application.getPromptTemplateId())) {
            try {
                PromptTemplateEntity template = promptTemplateService.getById(application.getPromptTemplateId());
                if (template != null && StringUtils.hasText(template.getContent())) {
                    systemPrompt.append(template.getContent()).append("\n\n");
                }
            } catch (Exception e) {
                log.warn("加载提示词模板失败: {}, {}", application.getPromptTemplateId(), e.getMessage());
            }
        }

        // 知识库检索
        List<String> kbIds = parseStringList(application.getKnowledgeBaseIds());
        if (!kbIds.isEmpty()) {
            long retrievalStart = System.currentTimeMillis();
            try {
                List<RetrievalResult> results = hybridRetriever.retrieve(userMessage, kbIds, DEFAULT_TOP_K);
                if (traceCollector != null) {
                    recordTrace("retrieval", "search", traceId,
                            System.currentTimeMillis() - retrievalStart, "success",
                            "知识库检索: " + results.size() + " 条结果");
                }
                if (!results.isEmpty()) {
                    systemPrompt.append("以下是从知识库中检索到的相关内容，请据此回答用户问题：\n\n");
                    for (int i = 0; i < results.size(); i++) {
                        RetrievalResult r = results.get(i);
                        systemPrompt.append("【片段").append(i + 1).append("】")
                                .append("来源：").append(getDocumentName(r)).append("\n")
                                .append("内容：").append(r.getContent()).append("\n\n");

                        Map<String, Object> source = new HashMap<>();
                        source.put("paragraphId", r.getParagraphId());
                        source.put("documentId", r.getDocumentId());
                        source.put("knowledgeBaseId", r.getKnowledgeBaseId());
                        source.put("content", r.getContent());
                        source.put("score", r.getFinalScore());
                        source.put("documentName", getDocumentName(r));
                        retrievalSources.add(source);
                    }
                    systemPrompt.append("请在回答时引用上述知识库内容，如未涉及请如实告知。\n\n");
                }
            } catch (Exception e) {
                if (traceCollector != null) {
                    recordTrace("retrieval", "search", traceId,
                            System.currentTimeMillis() - retrievalStart, "fail",
                            "知识库检索失败: " + e.getMessage());
                }
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 应用描述兜底
        if (systemPrompt.length() == 0 && StringUtils.hasText(application.getDescription())) {
            systemPrompt.append(application.getDescription());
        }

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt.toString().trim()));
        }

        if (requestMessages != null && !requestMessages.isEmpty()) {
            // OpenAI 兼容：使用请求携带的完整对话上下文（含当前消息）
            for (ChatRequestDTO.ConversationMessage m : requestMessages) {
                if (StringUtils.hasText(m.getRole()) && StringUtils.hasText(m.getContent())) {
                    messages.add(new ChatMessage(m.getRole().toLowerCase(), m.getContent()));
                }
            }
        } else {
            // 内部路径：加载数据库历史消息，并追加当前用户消息
            List<ChatMessage> historyMessages = loadHistoryMessages(
                    sessionId, application.getMaxTurns(), null);
            messages.addAll(historyMessages);
            messages.add(ChatMessage.user(userMessage));
        }

        return new Context(messages, retrievalSources);
    }

    private List<ChatMessage> loadHistoryMessages(String sessionId, Integer maxTurns, String excludeMessageId) {
        if (!StringUtils.hasText(sessionId)) {
            return new ArrayList<>();
        }
        int limit = maxTurns != null && maxTurns > 0 ? maxTurns * 2 : 20;
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .in(ChatMessageEntity::getRole, "user", "assistant")
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .last("LIMIT " + (limit + 1));
        List<ChatMessageEntity> recent = chatMessageService.list(wrapper);
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessageEntity e = recent.get(i);
            if (excludeMessageId != null && e.getId().equals(excludeMessageId)) {
                continue;
            }
            messages.add(new ChatMessage(e.getRole(), e.getContent()));
        }
        return messages;
    }

    private ChatResponse callModel(ApplicationEntity application, List<ChatMessage> messages, String traceId) {
        OpenAICompatibleClient client = getClient(application);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .temperature(application.getTemperature() != null ? application.getTemperature() : 0.7)
                .build();
        long llmStart = System.currentTimeMillis();
        try {
            ChatResponse response = client.chat(chatRequest);
            if (traceCollector != null) {
                recordTrace("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "success",
                        "模型: " + getModelName(application.getModelId()) + ", tokens: " + response.getTotalTokens());
            }
            return response;
        } catch (Exception e) {
            if (traceCollector != null) {
                recordTrace("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "fail",
                        "模型调用失败: " + e.getMessage());
            }
            log.error("模型调用失败: app={}, model={}", application.getName(), application.getModelId(), e);
            throw new ApiException("模型调用失败: " + e.getMessage());
        }
    }

    private Stream<ChatChunk> callModelStream(ApplicationEntity application,
                                               List<ChatMessage> messages,
                                               String traceId) {
        OpenAICompatibleClient client = getClient(application);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .temperature(application.getTemperature() != null ? application.getTemperature() : 0.7)
                .build();
        return client.streamChat(chatRequest);
    }

    private OpenAICompatibleClient getClient(ApplicationEntity application) {
        if (StringUtils.hasText(application.getModelId())) {
            return modelService.getClient(application.getModelId());
        }
        return modelService.getDefaultClient();
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

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = JSON.parseArray(json);
            return array.stream()
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}, json={}", e.getMessage(), json);
            return new ArrayList<>();
        }
    }

    private String getDocumentName(RetrievalResult r) {
        if (r.getMetadata() != null) {
            Object name = r.getMetadata().get("title");
            if (name != null) {
                return name.toString();
            }
        }
        return r.getDocumentId() != null ? r.getDocumentId() : "未知文档";
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

    /**
     * 注入长期记忆到消息列表（在 system prompt 后追加用户画像上下文）
     */
    private void injectLongTermMemory(ChatRequestDTO request, List<ChatMessage> messages) {
        injectLongTermMemory(request, messages, UserContext.getUserId());
    }

    private void injectLongTermMemory(ChatRequestDTO request, List<ChatMessage> messages, String userId) {
        try {
            if (!StringUtils.hasText(userId)) {
                return;
            }
            String appId = request.getApplicationId();
            if (!StringUtils.hasText(appId)) {
                return;
            }

            // 一次查询获取全部激活记忆，按维度分组，避免每个维度单独查询
            List<LongTermMemoryEntity> allMemories = longTermMemoryService.findActiveAll(userId, appId);
            if (allMemories == null || allMemories.isEmpty()) {
                return;
            }

            List<String> dimensionOrder = Arrays.asList("preference", "background", "convention", "goal");
            Map<String, String> dimLabels = Map.of(
                    "preference", "【用户偏好】",
                    "background", "【用户背景】",
                    "convention", "【用户习惯】",
                    "goal", "【用户目标】");

            StringBuilder memoryPrompt = new StringBuilder();
            for (String dim : dimensionOrder) {
                List<LongTermMemoryEntity> memories = allMemories.stream()
                        .filter(m -> dim.equals(m.getDimension()))
                        .toList();
                if (memories.isEmpty()) {
                    continue;
                }
                memoryPrompt.append(dimLabels.getOrDefault(dim, "【" + dim + "】")).append("\n");
                for (LongTermMemoryEntity m : memories) {
                    memoryPrompt.append("- ").append(m.getContent()).append("\n");
                    longTermMemoryService.incrementTrigger(m.getId());
                }
                memoryPrompt.append("\n");
            }

            if (memoryPrompt.length() > 0) {
                String fullMemoryContext = "以下是关于当前用户的长期记忆信息，请在回答时参考：\n\n"
                        + memoryPrompt.toString().trim();
                // 插入到第一条消息之后（通常是 system prompt 之后）
                if (!messages.isEmpty()) {
                    messages.add(1, ChatMessage.system(fullMemoryContext));
                } else {
                    messages.add(ChatMessage.system(fullMemoryContext));
                }
                log.debug("已注入长期记忆: userId={}, appId={}, length={}",
                        userId, appId, memoryPrompt.length());
            }
        } catch (Exception e) {
            log.warn("注入长期记忆失败: {}", e.getMessage());
        }
    }

    private record Context(List<ChatMessage> messages,
                           List<Map<String, Object>> retrievalSources) {
    }
}