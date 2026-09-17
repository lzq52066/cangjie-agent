package cn.cangjiecloud.chat.context;

import cn.cangjiecloud.core.harness.context.ContextContributor;
import cn.cangjiecloud.core.harness.context.ContextFragment;
import cn.cangjiecloud.core.harness.context.ContextRequest;
import cn.cangjiecloud.core.harness.context.ContextSlot;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.rule.RuleEvaluationResult;
import cn.cangjiecloud.prompt.rule.RuleEvaluator;
import cn.cangjiecloud.prompt.service.IRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则指令（SYSTEM 槽位）：规则引擎按本轮输入评估匹配后编译出的指令段。
 */
@Component
@RequiredArgsConstructor
public class RuleContributor implements ContextContributor {

    private final IRuleService ruleService;
    private final RuleEvaluator ruleEvaluator;

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public ContextSlot slot() {
        return ContextSlot.SYSTEM;
    }

    @Override
    public int getOrder() {
        return 130;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ContextFragment contribute(ContextRequest request) {
        Object value = request.getAttributes().get(ContextAttrs.RULE_IDS);
        List<String> ruleIds = value instanceof List<?> ids ? (List<String>) ids : List.of();
        if (ruleIds.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }
        List<RuleEntity> rules = ruleService.listByIds(ruleIds).stream()
                .filter(r -> "active".equals(r.getStatus()))
                .toList();
        if (rules.isEmpty()) {
            return ContextFragment.empty(slot(), name());
        }
        Map<String, Object> ruleContext = new HashMap<>();
        ruleContext.put("applicationId", request.getApplicationId());
        ruleContext.put("applicationName", request.getAttributes().get(ContextAttrs.APPLICATION_NAME));
        List<RuleEvaluationResult> matchedRules = ruleEvaluator.evaluate(rules, request.getUserQuery(), ruleContext);
        String instructions = ruleEvaluator.compileInstructions(matchedRules);
        if (!StringUtils.hasText(instructions)) {
            return ContextFragment.empty(slot(), name());
        }
        return ContextFragment.of(slot(), name(), List.of(ChatMessage.system(instructions)));
    }
}
