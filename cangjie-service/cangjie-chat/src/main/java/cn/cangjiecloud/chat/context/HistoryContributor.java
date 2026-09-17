package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.entity.ChatMessageEntity;
import cn.cangjiecloud.chat.service.IChatMessageService;
import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮历史（HISTORY 槽位）。
 * <p>
 * 两条路径共用：OpenAI 兼容时直接接管请求携带的完整会话（含当前输入，此时
 * 摘要与用户消息贡献者让位）；内部路径按 maxTurns 加载库内消息，超 token 预算
 * 时从最旧丢弃（始终保留最新一条）。
 * <p>
 * 调度序提前到 RAG 之前（150 < 200）：查询改写需要历史，加载结果经
 * {@link ContextAttrs#HISTORY_MESSAGES} 复用，避免二次查库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryContributor implements ContextContributor {

    private final IChatMessageService chatMessageService;

    /** 历史消息 token 预算（超出后从最旧消息开始截断，0 表示不限制） */
    @Value("${cangjie.chat.history.max-tokens:6000}")
    private int historyMaxTokens;

    @Override
    public String name() {
        return "history";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.HISTORY;
    }

    @Override
    public int getOrder() {
        return 150;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        Object conversation = request.getAttributes().get(ContextAttrs.REQUEST_CONVERSATION);
        if (conversation instanceof List<?> messages && !messages.isEmpty()) {
            List<ChatMessage> full = (List<ChatMessage>) messages;
            request.getAttributes().put(ContextAttrs.HISTORY_MESSAGES, full);
            return ContextFragment.of(slot(), name(), new ArrayList<>(full));
        }
        List<ChatMessage> history = loadFromDb(request);
        request.getAttributes().put(ContextAttrs.HISTORY_MESSAGES, history);
        return ContextFragment.of(slot(), name(), history);
    }

    private List<ChatMessage> loadFromDb(ContextRequest request) {
        if (!StringUtils.hasText(request.getSessionId())) {
            return new ArrayList<>();
        }
        Integer maxTurns = request.getMaxTurns();
        int limit = maxTurns != null && maxTurns > 0 ? maxTurns * 2 : 20;
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, request.getSessionId())
                .in(ChatMessageEntity::getRole, "user", "assistant")
                .orderByDesc(ChatMessageEntity::getCreateTime)
                .last("LIMIT " + (limit + 1));
        List<ChatMessageEntity> recent = chatMessageService.list(wrapper);
        List<ChatMessage> messages = new ArrayList<>();
        // 查询按时间倒序，回填为正序
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessageEntity entity = recent.get(i);
            messages.add(ChatMessage.builder().role(entity.getRole()).content(entity.getContent()).build());
        }

        // token 预算：超预算时从最旧消息开始截断（始终保留最新一条）
        if (historyMaxTokens > 0 && messages.size() > 1) {
            int total = messages.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
            int dropCount = 0;
            while (total > historyMaxTokens && dropCount < messages.size() - 1) {
                total -= estimateTokens(messages.get(dropCount).getContent());
                dropCount++;
            }
            if (dropCount > 0) {
                log.info("历史消息按 token 预算截断: sessionId={}, 截断 {} 条", request.getSessionId(), dropCount);
                messages = new ArrayList<>(messages.subList(dropCount, messages.size()));
            }
        }
        return messages;
    }

    private int estimateTokens(String content) {
        return content == null ? 0 : (int) (content.length() * 0.75);
    }
}
