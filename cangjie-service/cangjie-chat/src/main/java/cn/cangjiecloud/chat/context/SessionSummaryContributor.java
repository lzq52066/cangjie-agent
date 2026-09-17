package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.chat.entity.ChatSessionEntity;
import cn.cangjiecloud.chat.service.IChatSessionService;
import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 会话摘要（SYSTEM_PRE 槽位，装配在最前）。
 * <p>
 * 弥补历史被 token 预算截断后丢失的早期上下文。改造前摘要内嵌在历史加载里，
 * 现按槽位设计置顶，且不再混入查询改写的历史。
 */
@Component
@RequiredArgsConstructor
public class SessionSummaryContributor implements ContextContributor {

    private final IChatSessionService chatSessionService;

    @Override
    public String name() {
        return "sessionSummary";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.SYSTEM_PRE;
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public boolean supports(ContextRequest request) {
        // OpenAI 兼容路径不读取库内会话状态
        return StringUtils.hasText(request.getSessionId())
                && !request.getAttributes().containsKey(ContextAttrs.REQUEST_CONVERSATION);
    }

    @Override
    public ContextFragment contribute(ContextRequest request) {
        ChatSessionEntity session = chatSessionService.getBySessionId(request.getSessionId());
        if (session == null || !StringUtils.hasText(session.getSummary())) {
            return ContextFragment.empty(slot(), name());
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(
                "【会话摘要】以下是本会话早前内容的摘要，供你了解上下文：\n" + session.getSummary())));
    }
}
