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
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.observability.TraceCollector;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.impl.ModelServiceImpl;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final IApplicationService applicationService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final ModelServiceImpl modelService;
    private final HybridRetriever hybridRetriever;
    private final IPromptTemplateService promptTemplateService;

    @Autowired(required = false)
    private TraceCollector traceCollector;

    private static final int DEFAULT_TOP_K = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResponseDTO chat(ChatRequestDTO request) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // 1. 获取应用配置
        ApplicationEntity application = applicationService.getById(request.getApplicationId());
        if (application == null) {
            throw new ApiException("应用不存在: " + request.getApplicationId());
        }
        if (!"published".equals(application.getStatus())) {
            throw new ApiException("应用未发布，无法对话");
        }

        // 2. 获取/创建会话
        ChatSessionEntity session = getOrCreateSession(request, application);

        // 3. 保存用户消息
        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setSessionId(session.getSessionId());
        userMessage.setApplicationId(application.getId());
        userMessage.setRole("user");
        userMessage.setContent(request.getMessage());
        chatMessageService.save(userMessage);
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);

        // 4. 构建 system prompt（含提示词模板 + 知识库检索结果）
        List<Map<String, Object>> retrievalSources = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(application, request.getMessage(), retrievalSources, traceId);

        // 5. 加载历史消息
        List<ChatMessage> historyMessages = loadHistoryMessages(
                session.getSessionId(), application.getMaxTurns(), userMessage.getId());

        // 6. 构建完整消息列表
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.addAll(historyMessages);
        messages.add(ChatMessage.user(request.getMessage()));

        // 7. 调用模型
        OpenAICompatibleClient client = getClient(application);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .temperature(application.getTemperature() != null ? application.getTemperature() : 0.7)
                .build();
        ChatResponse chatResponse;
        long llmStart = System.currentTimeMillis();
        try {
            chatResponse = client.chat(chatRequest);
            // 记录模型调用追踪
            if (traceCollector != null) {
                traceCollector.record("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "success",
                        "模型: " + getModelName(application.getModelId()) + ", tokens: " + chatResponse.getTotalTokens());
            }
        } catch (Exception e) {
            // 记录模型调用失败追踪
            if (traceCollector != null) {
                traceCollector.record("chat", "llm_call", traceId,
                        System.currentTimeMillis() - llmStart, "fail",
                        "模型调用失败: " + e.getMessage());
            }
            log.error("模型调用失败: app={}, model={}", application.getName(), application.getModelId(), e);
            throw new ApiException("模型调用失败: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;

        // 8. 保存 AI 回复消息
        ChatMessageEntity aiMessage = new ChatMessageEntity();
        aiMessage.setSessionId(session.getSessionId());
        aiMessage.setApplicationId(application.getId());
        aiMessage.setRole("assistant");
        aiMessage.setContent(chatResponse.getContent());
        aiMessage.setTokens(chatResponse.getTotalTokens());
        aiMessage.setRetrievalSources(retrievalSources.isEmpty() ? null : JSON.toJSONString(retrievalSources));
        aiMessage.setDuration(duration);
        chatMessageService.save(aiMessage);

        // 9. 更新会话统计
        session.setMessageCount(session.getMessageCount() + 1);
        session.setTokensUsed((session.getTokensUsed() == null ? 0 : session.getTokensUsed())
                + (chatResponse.getTotalTokens() > 0 ? chatResponse.getTotalTokens() : 0));
        chatSessionService.updateById(session);

        // 记录对话完成追踪
        if (traceCollector != null) {
            traceCollector.record("chat", "send", traceId, duration, "success",
                    "应用: " + application.getName() + ", 总tokens: " + chatResponse.getTotalTokens());
        }

        log.info("对话完成: app={}, session={}, duration={}ms, tokens={}",
                application.getName(), session.getSessionId(), duration, chatResponse.getTotalTokens());

        return ChatResponseDTO.builder()
                .sessionId(session.getSessionId())
                .message(chatResponse.getContent())
                .role("assistant")
                .retrievalSources(retrievalSources)
                .tokens(chatResponse.getTotalTokens())
                .duration(duration)
                .build();
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
        // 调用方传入自定义 sessionId 时直接复用（如渠道用户 ch_{channelId}_{openId} 多轮记忆），否则生成 UUID
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

    private String buildSystemPrompt(ApplicationEntity application, String userMessage,
                                     List<Map<String, Object>> retrievalSources, String traceId) {
        StringBuilder sb = new StringBuilder();

        // 提示词模板内容
        if (StringUtils.hasText(application.getPromptTemplateId())) {
            try {
                PromptTemplateEntity template = promptTemplateService.getById(application.getPromptTemplateId());
                if (template != null && StringUtils.hasText(template.getContent())) {
                    sb.append(template.getContent()).append("\n\n");
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
                // 记录检索追踪
                if (traceCollector != null) {
                    traceCollector.record("retrieval", "search", traceId,
                            System.currentTimeMillis() - retrievalStart, "success",
                            "知识库检索: " + results.size() + " 条结果");
                }
                if (!results.isEmpty()) {
                    sb.append("以下是从知识库中检索到的相关内容，请据此回答用户问题：\n\n");
                    for (int i = 0; i < results.size(); i++) {
                        RetrievalResult r = results.get(i);
                        sb.append("【片段").append(i + 1).append("】")
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
                    sb.append("请在回答时引用上述知识库内容，如未涉及请如实告知。\n\n");
                }
            } catch (Exception e) {
                // 记录检索失败追踪
                if (traceCollector != null) {
                    traceCollector.record("retrieval", "search", traceId,
                            System.currentTimeMillis() - retrievalStart, "fail",
                            "知识库检索失败: " + e.getMessage());
                }
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 应用描述作为兜底 system prompt
        if (sb.length() == 0 && StringUtils.hasText(application.getDescription())) {
            sb.append(application.getDescription());
        }

        return sb.toString().trim();
    }

    private List<ChatMessage> loadHistoryMessages(String sessionId, Integer maxTurns,
                                                  String currentMessageId) {
        int limit = maxTurns != null && maxTurns > 0 ? maxTurns * 2 : 20;
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .in(ChatMessageEntity::getRole, "user", "assistant")
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .last("LIMIT " + (limit + 1));
        List<ChatMessageEntity> recent = chatMessageService.list(wrapper);
        // 反转为时间正序，跳过当前轮刚保存的用户消息（由调用方重新加入）
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessageEntity e = recent.get(i);
            if (e.getId().equals(currentMessageId)) {
                continue;
            }
            messages.add(new ChatMessage(e.getRole(), e.getContent()));
        }
        return messages;
    }

    private OpenAICompatibleClient getClient(ApplicationEntity application) {
        if (StringUtils.hasText(application.getModelId())) {
            return modelService.getClient(application.getModelId());
        }
        return modelService.getDefaultClient();
    }

    /**
     * 获取模型显示名称，查不到时回退为模型 ID
     */
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
}
