package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.prompt.service.PromptCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 提示词模板（SYSTEM 槽位）：应用绑定的人设与指令，经缓存服务加载。
 */
@Component
@RequiredArgsConstructor
public class PromptTemplateContributor implements ContextContributor {

    private final PromptCacheService promptCacheService;

    @Override
    public String name() {
        return "promptTemplate";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.SYSTEM;
    }

    @Override
    public ContextFragment contribute(ContextRequest request) {
        Object templateId = request.getAttributes().get(ContextAttrs.PROMPT_TEMPLATE_ID);
        if (!(templateId instanceof String id) || !StringUtils.hasText(id)) {
            return ContextFragment.empty(slot(), name());
        }
        String content = promptCacheService.getContent(id);
        if (!StringUtils.hasText(content)) {
            return ContextFragment.empty(slot(), name());
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(content)));
    }
}
