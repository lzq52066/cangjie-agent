package cn.cangjiecloud.chat.service;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆（滚动摘要）服务
 * <p>
 * 会话消息每累计一定数量，异步调用模型将历史对话压缩为摘要存回会话；
 * 后续历史加载时以"会话摘要 + 最近 N 条消息"重建上下文，
 * 使长对话在有限窗口内不丢失早期信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryService {

    private final IModelService modelService;
    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;

    /** 会话摘要开关 */
    @Value("${cangjie.chat.session.summary-enabled:true}")
    private boolean summaryEnabled;

    /** 每新增多少条消息触发一次滚动摘要 */
    @Value("${cangjie.chat.session.summary-every:10}")
    private int summaryEvery;

    /** 摘要使用的模型（可选；为空时用应用绑定模型/默认模型） */
    @Value("${cangjie.chat.session.summary-model-id:}")
    private String summaryModelId;

    /** 正在生成摘要的会话集合：同一会话同一时刻只允许一个摘要任务，避免并发重复调用模型 */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 检查并触发滚动摘要（异步执行，条件判断放在异步线程内避免阻塞对话主流程）
     */
    @Async
    public void maybeSummarizeAsync(String sessionId, String fallbackModelId) {
        if (!summaryEnabled || !StringUtils.hasText(sessionId)) {
            return;
        }
        // CAS 抢占：添加失败说明该会话已有摘要任务在执行，直接跳过
        if (!inFlight.add(sessionId)) {
            return;
        }
        try {
            ChatSessionEntity session = chatSessionService.getBySessionId(sessionId);
            if (session == null) {
                return;
            }
            int messageCount = session.getMessageCount() != null ? session.getMessageCount() : 0;
            int summarized = session.getSummaryMsgCount() != null ? session.getSummaryMsgCount() : 0;
            if (messageCount - summarized < summaryEvery) {
                return;
            }
            doSummarize(session, fallbackModelId);
        } catch (Exception e) {
            log.warn("会话摘要生成失败: sessionId={}", sessionId, e);
        } finally {
            inFlight.remove(sessionId);
        }
    }

    private void doSummarize(ChatSessionEntity session, String fallbackModelId) {
        List<ChatMessageEntity> messages = chatMessageService.listBySession(session.getSessionId());
        if (messages.isEmpty()) {
            return;
        }
        // 摘要素材：旧摘要 + 最近消息（覆盖未摘要部分并适当外扩）
        int materialCount = Math.min(messages.size(), summaryEvery + 10);
        List<ChatMessageEntity> material = messages.subList(messages.size() - materialCount, messages.size());

        StringBuilder transcript = new StringBuilder();
        if (StringUtils.hasText(session.getSummary())) {
            transcript.append("【既有摘要】\n").append(session.getSummary()).append("\n\n");
        }
        transcript.append("【新增对话】\n");
        for (ChatMessageEntity message : material) {
            if (!"user".equals(message.getRole()) && !"assistant".equals(message.getRole())) {
                continue;
            }
            transcript.append("user".equals(message.getRole()) ? "用户: " : "AI: ")
                    .append(truncate(message.getContent(), 500)).append("\n");
        }

        OpenAICompatibleClient client = resolveClient(session, fallbackModelId);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("你是会话记录助手，负责把对话历史压缩为简洁摘要。"),
                        ChatMessage.user("""
                                请把以下会话内容总结为一段不超过 200 字的会话摘要，要求：
                                1. 保留关键事实、用户诉求、已达成的结论与未决事项
                                2. 合并旧摘要与新增对话，不要遗漏旧摘要中的重要信息
                                3. 直接输出摘要正文，不要任何前缀或解释
                                
                                """ + transcript)))
                .temperature(0.3)
                .build();
        ChatResponse response = client.chat(request);
        if (response == null || !StringUtils.hasText(response.getContent())) {
            return;
        }

        session.setSummary(response.getContent().trim());
        session.setSummaryMsgCount(session.getMessageCount() != null ? session.getMessageCount() : 0);
        chatSessionService.updateById(session);
        log.info("会话摘要已更新: sessionId={}, messageCount={}", session.getSessionId(), session.getSummaryMsgCount());
    }

    private OpenAICompatibleClient resolveClient(ChatSessionEntity session, String fallbackModelId) {
        if (StringUtils.hasText(summaryModelId)) {
            return modelService.getClient(summaryModelId);
        }
        String modelId = StringUtils.hasText(session.getModelId()) ? session.getModelId() : fallbackModelId;
        return StringUtils.hasText(modelId) ? modelService.getClient(modelId) : modelService.getDefaultClient();
    }

    private String truncate(String content, int max) {
        if (content == null) {
            return "";
        }
        return content.length() <= max ? content : content.substring(0, max) + "...";
    }
}
