package cn.cangjiecloud.prompt.rule;

import cn.cangjiecloud.prompt.entity.RuleEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则评估器 — 编程式规则引擎
 * <p>
 * 负责将 RuleEntity 的条件（condition）与当前对话上下文匹配，
 * 返回匹配的规则及其动作（action），注入到 LLM 的 system prompt 中。
 * <p>
 * 支持三种条件模式：
 * <ul>
 *   <li><b>关键词匹配</b>：condition 为逗号分隔的关键词列表，命中任一即匹配</li>
 *   <li><b>正则匹配</b>：condition 以 regex: 开头，进行正则匹配</li>
 *   <li><b>始终生效</b>：condition 为空，直接匹配（背景规则）</li>
 * </ul>
 */
@Slf4j
@Component
public class RuleEvaluator {

    private static final String REGEX_PREFIX = "regex:";

    /**
     * 评估规则列表，返回匹配的规则结果
     *
     * @param rules    待评估的规则列表
     * @param userInput 用户输入文本
     * @param context   额外上下文（历史消息、应用信息等）
     * @return 匹配的评估结果，按优先级降序
     */
    public List<RuleEvaluationResult> evaluate(List<RuleEntity> rules,
                                                String userInput,
                                                Map<String, Object> context) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<RuleEvaluationResult> results = new ArrayList<>();
        for (RuleEntity rule : rules) {
            RuleEvaluationResult result = evaluateSingle(rule, userInput);
            if (result.isMatched()) {
                results.add(result);
            }
        }

        // 按优先级降序排序
        results.sort(Comparator.comparing(
                r -> r.getRule().getPriority() != null ? r.getRule().getPriority() : 0,
                Comparator.reverseOrder()));

        return results;
    }

    /**
     * 评估单条规则
     */
    public RuleEvaluationResult evaluateSingle(RuleEntity rule,
                                                String userInput) {
        if (!"active".equals(rule.getStatus())) {
            return RuleEvaluationResult.builder()
                    .rule(rule).matched(false).score(0).context(Map.of()).build();
        }

        String condition = rule.getCondition();
        boolean matched;
        double score;

        if (!StringUtils.hasText(condition)) {
            // 无条件的规则（背景规则），始终匹配，但优先级较低
            matched = true;
            score = 0.3;
        } else if (condition.startsWith(REGEX_PREFIX)) {
            // 正则匹配
            String regex = condition.substring(REGEX_PREFIX.length()).trim();
            matched = matchRegex(regex, userInput);
            score = matched ? 0.9 : 0;
        } else {
            // 关键词匹配
            matched = matchKeywords(condition, userInput);
            score = matched ? 0.8 : 0;
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("ruleId", rule.getId());
        snapshot.put("ruleName", rule.getName());
        snapshot.put("condition", condition);
        snapshot.put("matched", matched);

        return RuleEvaluationResult.builder()
                .rule(rule)
                .matched(matched)
                .score(score)
                .context(snapshot)
                .build();
    }

    /**
     * 将匹配的规则结果编译为 LLM system prompt 指令
     *
     * @param results 匹配的评估结果
     * @return 格式化的规则指令文本
     */
    public String compileInstructions(List<RuleEvaluationResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【行为规则】请严格遵守以下规则：\n\n");

        for (RuleEvaluationResult result : results) {
            RuleEntity rule = result.getRule();
            sb.append("- ").append(rule.getName());
            if (StringUtils.hasText(rule.getDescription())) {
                sb.append("：").append(rule.getDescription());
            }
            if (StringUtils.hasText(rule.getAction())) {
                sb.append("\n  执行动作：").append(rule.getAction());
            }
            sb.append("\n");
        }

        log.debug("规则引擎编译完成: {} 条规则匹配", results.size());
        return sb.toString();
    }

    /**
     * 获取匹配规则的动作列表
     */
    public List<String> getMatchedActions(List<RuleEvaluationResult> results) {
        return results.stream()
                .filter(RuleEvaluationResult::isMatched)
                .map(r -> r.getRule().getAction())
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    private boolean matchKeywords(String condition, String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return false;
        }
        String[] keywords = condition.split("[,，]");
        for (String kw : keywords) {
            String trimmed = kw.trim();
            if (StringUtils.hasText(trimmed) && userInput.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchRegex(String regex, String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return false;
        }
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(userInput)
                    .find();
        } catch (Exception e) {
            log.warn("正则匹配异常: regex={}, error={}", regex, e.getMessage());
            return false;
        }
    }
}