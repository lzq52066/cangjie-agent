package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本轮用户输入（USER 槽位，不可压缩、始终位于末尾）。
 * <p>
 * OpenAI 兼容路径下当前输入已包含在请求会话中，由历史贡献者整体接管，此处让位。
 */
@Component
public class UserMessageContributor implements ContextContributor {

    @Override
    public String name() {
        return "userMessage";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.USER;
    }

    @Override
    public int getOrder() {
        return 650;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return !request.getAttributes().containsKey(ContextAttrs.REQUEST_CONVERSATION);
    }

    @Override
    public ContextFragment contribute(ContextRequest request) {
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.user(request.getUserQuery())));
    }
}
