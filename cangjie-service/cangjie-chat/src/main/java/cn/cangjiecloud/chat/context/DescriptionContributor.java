package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 应用描述兜底（SYSTEM 槽位最后一棒）。
 * <p>
 * 与改造前一致：仅当提示词模板与知识库检索内容都为空时，用应用描述充当人设；
 * 技能/规则段不参与该判断（它们在改造前也追加于此条件之下）。因此调度序必须
 * 排在模板（100）与检索（200）之后。
 */
@Component
public class DescriptionContributor implements ContextContributor {

    /** 参与"系统提示词是否为空"判断的片段来源（技能/规则不计入） */
    private static final Set<String> PERSONA_SOURCES = Set.of("promptTemplate", "rag");

    @Override
    public String name() {
        return "applicationDescription";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.SYSTEM;
    }

    @Override
    public int getOrder() {
        return 260;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request.getAttributes().get(ContextAttrs.DESCRIPTION) instanceof String description
                && StringUtils.hasText(description);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        Object value = request.getAttributes().get(ContextAttrs.FRAGMENTS);
        List<ContextFragment> produced = value instanceof List<?> list ? (List<ContextFragment>) list : List.of();
        boolean hasPersona = produced.stream()
                .anyMatch(f -> PERSONA_SOURCES.contains(f.getSource()));
        if (hasPersona) {
            return ContextFragment.empty(slot(), name());
        }
        String description = (String) request.getAttributes().get(ContextAttrs.DESCRIPTION);
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(description)));
    }
}
