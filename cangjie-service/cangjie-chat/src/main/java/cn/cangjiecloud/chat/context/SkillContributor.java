package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ISkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 技能指令（SYSTEM 槽位）：应用绑定的 active 技能拼接为指令段。
 */
@Component
@RequiredArgsConstructor
public class SkillContributor implements ContextContributor {

    private final ISkillService skillService;

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.SYSTEM;
    }

    @Override
    public int getOrder() {
        return 120;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        Object value = request.getAttributes().get(ContextAttrs.SKILL_IDS);
        List<String> skillIds = value instanceof List<?> ids ? (List<String>) ids : List.of();
        if (skillIds.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }
        List<SkillEntity> skills = skillService.listByIds(skillIds).stream()
                .filter(s -> "active".equals(s.getStatus()))
                .toList();
        if (skills.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }
        StringBuilder prompt = new StringBuilder("【技能指令】以下是你可以使用的技能：\n\n");
        for (SkillEntity skill : skills) {
            prompt.append("技能：").append(skill.getName()).append("\n");
            if (StringUtils.hasText(skill.getDescription())) {
                prompt.append("描述：").append(skill.getDescription()).append("\n");
            }
            if (StringUtils.hasText(skill.getContent())) {
                prompt.append("指令：").append(skill.getContent()).append("\n");
            }
            prompt.append("\n");
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(prompt.toString().trim())));
    }
}
