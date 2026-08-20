package cn.cangjiecloud.prompt.rule;

import cn.cangjiecloud.prompt.entity.RuleEntity;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 规则评估结果
 */
@Data
@Builder
public class RuleEvaluationResult {

    /** 匹配的规则 */
    private RuleEntity rule;

    /** 是否匹配 */
    private boolean matched;

    /** 匹配度评分（0-1） */
    private double score;

    /** 评估上下文快照（供调试） */
    private Map<String, Object> context;
}