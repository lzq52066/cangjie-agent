package cn.cangjiecloud.prompt.rule;

import cn.cangjiecloud.prompt.entity.RuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleEvaluator} 单元测试。
 * <p>
 * 纯内存计算，不依赖数据库与 Spring 上下文，覆盖关键词/正则/无条件三种匹配模式、
 * 优先级排序、指令编译与动作提取。
 */
class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    private static RuleEntity rule(String id, String name, String condition, String status,
                                   Integer priority, String action, String description) {
        RuleEntity rule = new RuleEntity();
        rule.setId(id);
        rule.setName(name);
        rule.setCondition(condition);
        rule.setStatus(status);
        rule.setPriority(priority);
        rule.setAction(action);
        rule.setDescription(description);
        return rule;
    }

    /** 激活状态的普通规则 */
    private static RuleEntity activeRule(String id, String name, String condition, Integer priority) {
        return rule(id, name, condition, "active", priority, null, null);
    }

    @Nested
    @DisplayName("evaluate：批量评估与优先级排序")
    class Evaluate {

        @Test
        void 规则列表为空时返回空结果() {
            assertThat(evaluator.evaluate(null, "任意输入", Map.of())).isEmpty();
            assertThat(evaluator.evaluate(List.of(), "任意输入", Map.of())).isEmpty();
        }

        @Test
        void 只保留匹配规则并按优先级降序排序且空优先级视为零() {
            List<RuleEntity> rules = List.of(
                    activeRule("r1", "低优先级命中", "退款", 1),
                    activeRule("r2", "高优先级命中", "退款", 5),
                    activeRule("r3", "未命中", "发票", 99),
                    rule("r4", "背景规则", "  ", "active", null, null, null));

            List<RuleEvaluationResult> results = evaluator.evaluate(rules, "我要申请退款", Map.of());

            assertThat(results).extracting(r -> r.getRule().getId())
                    .containsExactly("r2", "r1", "r4");
            assertThat(results).allMatch(RuleEvaluationResult::isMatched);
            assertThat(results).extracting(RuleEvaluationResult::getScore)
                    .containsExactly(0.8, 0.8, 0.3);
        }

        @Test
        void 全部未命中时返回空列表() {
            List<RuleEntity> rules = List.of(
                    activeRule("r1", "未命中", "退款", 1),
                    rule("r2", "已停用", "退款", "inactive", 1, null, null));

            assertThat(evaluator.evaluate(rules, "你好", Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("evaluateSingle：单条规则匹配")
    class EvaluateSingle {

        @Test
        void 非激活规则直接不匹配且评分为零() {
            RuleEntity inactive = rule("r1", "停用规则", "退款", "inactive", 9, "动作", "描述");

            RuleEvaluationResult result = evaluator.evaluateSingle(inactive, "我要退款");

            assertThat(result.isMatched()).isFalse();
            assertThat(result.getScore()).isZero();
            assertThat(result.getRule()).isSameAs(inactive);
            assertThat(result.getContext()).isEmpty();
        }

        @Test
        void 关键词命中任一即匹配且支持中英文逗号与空白() {
            RuleEntity rule = activeRule("r1", "退款规则", "退款, 退货，换货", 1);

            RuleEvaluationResult hit = evaluator.evaluateSingle(rule, "我要退货");

            assertThat(hit.isMatched()).isTrue();
            assertThat(hit.getScore()).isEqualTo(0.8);
            assertThat(hit.getContext())
                    .containsEntry("ruleId", "r1")
                    .containsEntry("ruleName", "退款规则")
                    .containsEntry("condition", "退款, 退货，换货")
                    .containsEntry("matched", true);
        }

        @Test
        void 关键词未命中或输入为空时不匹配() {
            RuleEntity rule = activeRule("r1", "退款规则", "退款", 1);

            assertThat(evaluator.evaluateSingle(rule, "咨询发票").isMatched()).isFalse();
            assertThat(evaluator.evaluateSingle(rule, "咨询发票").getScore()).isZero();
            assertThat(evaluator.evaluateSingle(rule, null).isMatched()).isFalse();
            assertThat(evaluator.evaluateSingle(rule, "   ").isMatched()).isFalse();
        }

        @Test
        void 正则条件命中时评分更高且忽略大小写与换行() {
            RuleEntity rule = activeRule("r1", "打招呼", "regex:^HELLO.*WORLD$", 1);

            RuleEvaluationResult result = evaluator.evaluateSingle(rule, "hello\nbig world");

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getScore()).isEqualTo(0.9);
        }

        @Test
        void 非法正则表达式不抛异常且不匹配() {
            RuleEntity rule = activeRule("r1", "坏正则", "regex:[unclosed", 1);

            RuleEvaluationResult result = evaluator.evaluateSingle(rule, "任意输入");

            assertThat(result.isMatched()).isFalse();
            assertThat(result.getScore()).isZero();
        }

        @Test
        void 正则条件在输入为空时不匹配() {
            RuleEntity rule = activeRule("r1", "正则", "regex:abc", 1);

            assertThat(evaluator.evaluateSingle(rule, null).isMatched()).isFalse();
        }

        @Test
        void 无条件规则始终匹配且评分固定为零点三() {
            RuleEntity rule = rule("r1", "背景规则", "", "active", 0, null, null);

            RuleEvaluationResult hit = evaluator.evaluateSingle(rule, "任意输入");
            RuleEvaluationResult alsoHit = evaluator.evaluateSingle(rule, null);

            assertThat(hit.isMatched()).isTrue();
            assertThat(hit.getScore()).isEqualTo(0.3);
            assertThat(alsoHit.isMatched()).isTrue();
        }
    }

    @Nested
    @DisplayName("compileInstructions：规则编译为 system prompt")
    class CompileInstructions {

        @Test
        void 结果为空时返回空字符串() {
            assertThat(evaluator.compileInstructions(null)).isEmpty();
            assertThat(evaluator.compileInstructions(List.of())).isEmpty();
        }

        @Test
        void 编译包含名称描述与动作的规则() {
            RuleEntity rule = rule("r1", "礼貌用语", "你好", "active", 1,
                    "每句话都要礼貌", "与用户交流时保持礼貌");
            RuleEvaluationResult result = evaluator.evaluateSingle(rule, "你好");

            String text = evaluator.compileInstructions(List.of(result));

            assertThat(text).startsWith("【行为规则】请严格遵守以下规则：");
            assertThat(text).contains("- 礼貌用语：与用户交流时保持礼貌");
            assertThat(text).contains("执行动作：每句话都要礼貌");
        }

        @Test
        void 缺少描述与动作时只输出规则名称() {
            RuleEntity rule = activeRule("r1", "简单规则", "退款", 1);
            RuleEvaluationResult result = evaluator.evaluateSingle(rule, "退款");

            String text = evaluator.compileInstructions(List.of(result));

            assertThat(text).isEqualTo("【行为规则】请严格遵守以下规则：\n\n- 简单规则\n");
        }
    }

    @Nested
    @DisplayName("getMatchedActions：动作提取")
    class GetMatchedActions {

        @Test
        void 只返回匹配规则中非空的动作() {
            RuleEntity withAction = rule("r1", "规则一", "退款", "active", 2, "执行退款流程", null);
            RuleEntity blankAction = rule("r2", "规则二", "退款", "active", 1, "   ", null);
            RuleEntity unmatched = rule("r3", "规则三", "发票", "active", 3, "不该出现", null);

            List<RuleEvaluationResult> results = new ArrayList<>();
            results.add(evaluator.evaluateSingle(withAction, "退款"));
            results.add(evaluator.evaluateSingle(blankAction, "退款"));
            results.add(evaluator.evaluateSingle(unmatched, "退款"));

            assertThat(evaluator.getMatchedActions(results)).containsExactly("执行退款流程");
        }

        @Test
        void 没有匹配动作时返回空列表() {
            assertThat(evaluator.getMatchedActions(List.of())).isEmpty();
        }
    }
}
