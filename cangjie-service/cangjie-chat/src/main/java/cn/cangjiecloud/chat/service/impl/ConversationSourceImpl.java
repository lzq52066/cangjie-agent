package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.conversation.ConversationFilter;
import cn.cangjiecloud.core.conversation.ConversationSample;
import cn.cangjiecloud.core.conversation.ConversationSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话样本来源实现：把 {@code chat_message} 拆成问答对交给评测侧回流。
 * <p>
 * 问答配对规则放在这里而不是评测侧，因为只有对话模块知道「一轮」怎么界定；
 * 检索来源也在这里解析成干净的 ID 列表，评测侧不再去猜 {@code retrievalSources} 的形状。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSourceImpl implements ConversationSource {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /** 默认取多少条候选 */
    private static final int DEFAULT_LIMIT = 50;

    /**
     * 单次回流的硬上限。
     * <p>
     * 一次灌几千条进评测集既烧 token 又让「回归集」失焦：回流的价值是把这一批坏样本盯住，
     * 不是把线上对话全量搬过来。
     */
    private static final int MAX_LIMIT = 200;

    private final IChatMessageService chatMessageService;

    @Override
    public ConversationSample loadSample(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        ChatMessageEntity message = chatMessageService.getById(messageId);
        if (message == null) {
            return null;
        }
        if (ROLE_ASSISTANT.equals(message.getRole())) {
            return toSample(message, precedingQuestion(message));
        }
        if (ROLE_USER.equals(message.getRole())) {
            // 前端在用户气泡上点「加入评测集」也要能用：向后找这一轮的回答
            ChatMessageEntity answer = followingAnswer(message);
            return answer == null ? null : toSample(answer, message);
        }
        return null;
    }

    @Override
    public List<ConversationSample> searchSamples(ConversationFilter filter) {
        String applicationId = filter == null ? null : trimToNull(filter.applicationId());
        String feedback = filter == null ? null : trimToNull(filter.feedback());
        Boolean annotatedOnly = filter == null ? null : filter.annotatedOnly();
        int limit = Math.min(filter == null ? DEFAULT_LIMIT : filter.limitOrDefault(DEFAULT_LIMIT), MAX_LIMIT);

        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getRole, ROLE_ASSISTANT)
                .eq(applicationId != null, ChatMessageEntity::getApplicationId, applicationId)
                .eq(feedback != null, ChatMessageEntity::getFeedback, feedback);
        if (Boolean.TRUE.equals(annotatedOnly)) {
            wrapper.isNotNull(ChatMessageEntity::getAnnotation).ne(ChatMessageEntity::getAnnotation, "");
        } else if (Boolean.FALSE.equals(annotatedOnly)) {
            wrapper.and(w -> w.isNull(ChatMessageEntity::getAnnotation)
                    .or().eq(ChatMessageEntity::getAnnotation, ""));
        }
        List<ChatMessageEntity> answers = chatMessageService.list(wrapper
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .last("LIMIT " + limit));
        if (answers.isEmpty()) {
            return List.of();
        }

        // 批量把配对问题取回来：逐条查会变成 N+1，一次点击几百条查询把回流做成压测
        Map<String, List<ChatMessageEntity>> questionsBySession = loadUserMessages(answers);
        List<ConversationSample> samples = new ArrayList<>(answers.size());
        List<ChatMessageEntity> matched = new ArrayList<>(answers.size());
        for (ChatMessageEntity answer : answers) {
            matched.add(pickQuestion(questionsBySession.get(answer.getSessionId()), answer));
        }
        fillQuestionContents(matched);
        for (int i = 0; i < answers.size(); i++) {
            samples.add(toSample(answers.get(i), matched.get(i)));
        }
        return samples;
    }

    /**
     * 拉取候选会话里的用户消息，只取定序需要的三列。
     * <p>
     * 不投影的话会把整个会话的正文一次性读进内存，而配对只用时间戳：一次回流几百条时，
     * 未用到的正文就是纯粹的内存负担。正文留到 {@link #fillQuestionContents} 里按 ID 精确回捞。
     */
    private Map<String, List<ChatMessageEntity>> loadUserMessages(List<ChatMessageEntity> answers) {
        List<String> sessionIds = answers.stream()
                .map(ChatMessageEntity::getSessionId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        List<ChatMessageEntity> questions = chatMessageService.list(new LambdaQueryWrapper<ChatMessageEntity>()
                .select(ChatMessageEntity::getId, ChatMessageEntity::getSessionId,
                        ChatMessageEntity::getCreateTime)
                .in(ChatMessageEntity::getSessionId, sessionIds)
                .eq(ChatMessageEntity::getRole, ROLE_USER)
                .orderByAsc(ChatMessageEntity::getCreateTime));
        Map<String, List<ChatMessageEntity>> bySession = new LinkedHashMap<>();
        for (ChatMessageEntity question : questions) {
            bySession.computeIfAbsent(question.getSessionId(), key -> new ArrayList<>()).add(question);
        }
        return bySession;
    }

    /**
     * 按最终命中的问题 ID 批量补齐正文：一条 {@code IN} 查询，量级等于本次回流的样本数。
     */
    private void fillQuestionContents(List<ChatMessageEntity> matched) {
        List<String> ids = matched.stream()
                .filter(java.util.Objects::nonNull)
                .map(ChatMessageEntity::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<String, String> contentById = new LinkedHashMap<>();
        for (ChatMessageEntity question : chatMessageService.listByIds(ids)) {
            if (question != null) {
                contentById.put(question.getId(), question.getContent());
            }
        }
        for (ChatMessageEntity question : matched) {
            if (question != null) {
                question.setContent(contentById.get(question.getId()));
            }
        }
    }

    /**
     * 取该回答之前最近的一条用户消息（列表已按时间升序，从尾部往前找）。
     * <p>
     * 等于回答的时间戳也算：同一次请求里用户消息与 AI 回复可能拿到同一个时间，
     * 排除了反而会把上一轮的问题当成本轮的。
     */
    private ChatMessageEntity pickQuestion(List<ChatMessageEntity> sessionQuestions, ChatMessageEntity answer) {
        if (sessionQuestions == null || sessionQuestions.isEmpty()) {
            return null;
        }
        for (int i = sessionQuestions.size() - 1; i >= 0; i--) {
            ChatMessageEntity question = sessionQuestions.get(i);
            if (answer.getCreateTime() == null || question.getCreateTime() == null
                    || !question.getCreateTime().isAfter(answer.getCreateTime())) {
                return question;
            }
        }
        return null;
    }

    /**
     * 单条取数时的定序：同一时刻落库的消息（同事务批量插入）拿雪花 ID 兜底，
     * ID 位数固定，字典序与数值序一致。
     */
    private ChatMessageEntity precedingQuestion(ChatMessageEntity answer) {
        return chatMessageService.getOne(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, answer.getSessionId())
                .eq(ChatMessageEntity::getRole, ROLE_USER)
                .le(ChatMessageEntity::getCreateTime, answer.getCreateTime())
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .orderByDesc(ChatMessageEntity::getId)
                .last("LIMIT 1"), false);
    }

    private ChatMessageEntity followingAnswer(ChatMessageEntity question) {
        return chatMessageService.getOne(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, question.getSessionId())
                .eq(ChatMessageEntity::getRole, ROLE_ASSISTANT)
                .ge(ChatMessageEntity::getCreateTime, question.getCreateTime())
                .orderByAsc(ChatMessageEntity::getCreateTime)
                .orderByAsc(ChatMessageEntity::getId)
                .last("LIMIT 1"), false);
    }

    private ConversationSample toSample(ChatMessageEntity answer, ChatMessageEntity question) {
        List<String> documentIds = new ArrayList<>();
        List<String> paragraphIds = new ArrayList<>();
        readSources(answer.getRetrievalSources(), documentIds, paragraphIds);
        return new ConversationSample(answer.getId(), answer.getSessionId(), answer.getApplicationId(),
                question == null ? null : question.getContent(), answer.getContent(),
                answer.getFeedback(), trimToNull(answer.getAnnotation()),
                documentIds, paragraphIds, answer.getCreateTime());
    }

    /**
     * 解析 {@code retrievalSources}：一次检索同时给出文档与片段两级标注，
     * 正好对应评测侧的 referenceDocs / referenceChunks（后者是 contextRecall 的唯一依据）。
     * <p>
     * 坏 JSON 只丢标注、不丢样本：回流的问题是真实用户问过的，比标注更值钱。
     */
    private void readSources(String json, List<String> documentIds, List<String> paragraphIds) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        List<Map<String, Object>> sources;
        try {
            sources = JsonUtils.parseListOfMap(json);
        } catch (Exception e) {
            log.warn("检索来源解析失败，已忽略标注: {}", e.getMessage());
            return;
        }
        for (Map<String, Object> source : sources) {
            if (source == null) {
                continue;
            }
            String documentId = text(source.get("documentId"));
            String paragraphId = text(source.get("paragraphId"));
            if (documentId != null && !documentIds.contains(documentId)) {
                documentIds.add(documentId);
            }
            if (paragraphId != null && !paragraphIds.contains(paragraphId)) {
                paragraphIds.add(paragraphId);
            }
        }
    }

    private static String text(Object value) {
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
