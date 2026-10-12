package cn.cangjiecloud.chat.service.impl;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.conversation.ConversationFilter;
import cn.cangjiecloud.core.conversation.ConversationSample;
import cn.cangjiecloud.core.conversation.ConversationSource;
import cn.cangjiecloud.core.conversation.ConversationTurn;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * 一条用例最多带多少句历史。
     * <p>
     * 「那第二个呢」这类追问只有配上文才评得出东西；但把整场会话灌进每条用例，
     * 一次离线跑要把几十轮重复塞进模型，token 全花在同一批历史上。
     */
    private static final int MAX_HISTORY_MESSAGES = 12;

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
            ChatMessageEntity question = precedingQuestion(message);
            return toSample(message, question, historyMessages(message, question));
        }
        if (ROLE_USER.equals(message.getRole())) {
            // 前端在用户气泡上点「加入评测集」也要能用：向后找这一轮的回答
            ChatMessageEntity answer = followingAnswer(message);
            return answer == null ? null : toSample(answer, message, historyMessages(answer, message));
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

        // 批量把候选会话的时间线取回来：逐条查会变成 N+1，一次点击几百条查询把回流做成压测
        Map<String, List<ChatMessageEntity>> timelineBySession = loadSessionMessages(answers);
        List<ChatMessageEntity> questions = new ArrayList<>(answers.size());
        List<List<ChatMessageEntity>> histories = new ArrayList<>(answers.size());
        for (ChatMessageEntity answer : answers) {
            List<ChatMessageEntity> timeline =
                    timelineBySession.getOrDefault(answer.getSessionId(), List.of());
            int questionIndex = lastUserIndexAtOrBefore(timeline, answer);
            ChatMessageEntity question = questionIndex < 0 ? null : timeline.get(questionIndex);
            questions.add(question);
            histories.add(historyWindow(timeline, questionIndex));
        }

        List<ChatMessageEntity> involved = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            if (questions.get(i) != null) {
                involved.add(questions.get(i));
            }
            involved.addAll(histories.get(i));
        }
        Map<String, String> contentById = loadContents(involved);
        questions.forEach(question -> applyContent(question, contentById));
        histories.forEach(history -> history.forEach(message -> applyContent(message, contentById)));

        List<ConversationSample> samples = new ArrayList<>(answers.size());
        for (int i = 0; i < answers.size(); i++) {
            samples.add(toSample(answers.get(i), questions.get(i), histories.get(i)));
        }
        return samples;
    }

    /**
     * 拉取候选会话的消息时间线，只取定序需要的四列。
     * <p>
     * 不投影的话会把整个会话的正文一次性读进内存，而配对与历史窗口只用时间戳与 role：
     * 正文留到 {@link #loadContents} 里按最终命中的 ID 精确回捞。
     */
    private Map<String, List<ChatMessageEntity>> loadSessionMessages(List<ChatMessageEntity> answers) {
        List<String> sessionIds = answers.stream()
                .map(ChatMessageEntity::getSessionId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        List<ChatMessageEntity> messages = chatMessageService.list(new LambdaQueryWrapper<ChatMessageEntity>()
                .select(ChatMessageEntity::getId, ChatMessageEntity::getSessionId,
                        ChatMessageEntity::getRole, ChatMessageEntity::getCreateTime)
                .in(ChatMessageEntity::getSessionId, sessionIds)
                .in(ChatMessageEntity::getRole, List.of(ROLE_USER, ROLE_ASSISTANT))
                .orderByAsc(ChatMessageEntity::getCreateTime)
                .orderByAsc(ChatMessageEntity::getId));
        Map<String, List<ChatMessageEntity>> bySession = new LinkedHashMap<>();
        for (ChatMessageEntity message : messages) {
            bySession.computeIfAbsent(message.getSessionId(), key -> new ArrayList<>()).add(message);
        }
        return bySession;
    }

    /**
     * 按命中的消息 ID 批量补齐正文：一条 {@code IN} 查询，量级等于本次回流的样本数。
     */
    private Map<String, String> loadContents(List<ChatMessageEntity> messages) {
        List<String> ids = messages.stream()
                .filter(Objects::nonNull)
                .map(ChatMessageEntity::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> contentById = new LinkedHashMap<>();
        for (ChatMessageEntity message : chatMessageService.listByIds(ids)) {
            if (message != null) {
                contentById.put(message.getId(), message.getContent());
            }
        }
        return contentById;
    }

    private static void applyContent(ChatMessageEntity message, Map<String, String> contentById) {
        if (message != null) {
            message.setContent(contentById.get(message.getId()));
        }
    }

    /**
     * 取该回答之前最近的一条用户消息（时间线已按时间升序，从尾部往前找）。
     * <p>
     * 等于回答的时间戳也算：同一次请求里用户消息与 AI 回复可能拿到同一个时间，
     * 排除了反而会把上一轮的问题当成本轮的。
     *
     * @return 命中下标，会话里没有用户消息时返回 -1
     */
    private static int lastUserIndexAtOrBefore(List<ChatMessageEntity> timeline, ChatMessageEntity answer) {
        for (int i = timeline.size() - 1; i >= 0; i--) {
            ChatMessageEntity message = timeline.get(i);
            if (!ROLE_USER.equals(message.getRole())) {
                continue;
            }
            if (answer.getCreateTime() == null || message.getCreateTime() == null
                    || !message.getCreateTime().isAfter(answer.getCreateTime())) {
                return i;
            }
        }
        return -1;
    }

    /** 本轮问题之前的至多 {@code MAX_HISTORY_MESSAGES} 句，按时间升序 */
    private static List<ChatMessageEntity> historyWindow(List<ChatMessageEntity> timeline, int questionIndex) {
        if (questionIndex <= 0) {
            return List.of();
        }
        return new ArrayList<>(timeline.subList(Math.max(0, questionIndex - MAX_HISTORY_MESSAGES), questionIndex));
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

    private ConversationSample toSample(ChatMessageEntity answer, ChatMessageEntity question,
                                        List<ChatMessageEntity> history) {
        List<String> documentIds = new ArrayList<>();
        List<String> paragraphIds = new ArrayList<>();
        readSources(answer.getRetrievalSources(), documentIds, paragraphIds);
        List<ConversationTurn> turns = new ArrayList<>(history == null ? 0 : history.size());
        if (history != null) {
            for (ChatMessageEntity message : history) {
                if (StringUtils.hasText(message.getContent())) {
                    turns.add(new ConversationTurn(message.getRole(), message.getContent().trim()));
                }
            }
        }
        return new ConversationSample(answer.getId(), answer.getSessionId(), answer.getApplicationId(),
                question == null ? null : question.getContent(), answer.getContent(), turns,
                answer.getFeedback(), trimToNull(answer.getAnnotation()),
                documentIds, paragraphIds, answer.getCreateTime());
    }

    /**
     * 单条回流的历史：本轮问题之前的最后若干句。
     * <p>
     * 批量回流是从会话时间线切窗口，单条只拿得到一条消息，按时间倒着捞再翻正即可，
     * 不值得为它把整场会话读进内存。
     */
    private List<ChatMessageEntity> historyMessages(ChatMessageEntity answer, ChatMessageEntity question) {
        if (question == null || !StringUtils.hasText(answer.getSessionId())
                || question.getCreateTime() == null) {
            return List.of();
        }
        List<ChatMessageEntity> earlier = chatMessageService.list(new LambdaQueryWrapper<ChatMessageEntity>()
                .select(ChatMessageEntity::getId, ChatMessageEntity::getRole, ChatMessageEntity::getContent)
                .eq(ChatMessageEntity::getSessionId, answer.getSessionId())
                .in(ChatMessageEntity::getRole, List.of(ROLE_USER, ROLE_ASSISTANT))
                .ne(ChatMessageEntity::getId, answer.getId())
                .le(ChatMessageEntity::getCreateTime, question.getCreateTime())
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .orderByDesc(ChatMessageEntity::getId)
                .last("LIMIT " + (MAX_HISTORY_MESSAGES + 1)));
        List<ChatMessageEntity> chronological = new ArrayList<>(earlier);
        Collections.reverse(chronological);
        // 倒着捞会把本轮问题一起带进来（同刻落库的相邻轮次也算），摘掉它才是「这一轮之前」
        chronological.removeIf(message -> message.getRole() == null
                || Objects.equals(message.getId(), question.getId()));
        int from = Math.max(0, chronological.size() - MAX_HISTORY_MESSAGES);
        return new ArrayList<>(chronological.subList(from, chronological.size()));
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
