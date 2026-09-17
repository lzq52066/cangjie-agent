package cn.cangjiecloud.workflow.node;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConditionNode} 单元测试：数值/字符串比较、各操作符、变量占位符替换、无操作符兜底。
 */
class ConditionNodeTest {

    private final ConditionNode node = new ConditionNode();

    private boolean eval(String expression, Map<String, Object> inputs) {
        Map<String, Object> output = node.execute(inputs, Map.of("expression", expression));
        return (boolean) output.get("condition_result");
    }

    @Test
    // 覆盖场景：节点元信息
    void metadataShouldDescribeConditionNode() {
        assertThat(node.getType()).isEqualTo("condition");
        assertThat(node.getName()).isEqualTo("条件");
        assertThat(node.getDescription()).contains("条件表达式");
    }

    @Test
    // 覆盖场景：数值 >= 比较，变量占位符替换后成立/不成立两个分支
    void executeShouldCompareNumericGreaterOrEqual() {
        assertThat(eval("{score} >= 80", Map.of("score", 90))).isTrue();
        assertThat(eval("{score} >= 80", Map.of("score", 79))).isFalse();
        assertThat(eval("{score} >= 80", Map.of("score", 80))).isTrue();
    }

    @Test
    // 覆盖场景：数值 <= / > / < / == / != 全部操作符分支
    void executeShouldSupportAllNumericOperators() {
        assertThat(eval("1.5 <= 2", Map.of())).isTrue();
        assertThat(eval("3 > 5", Map.of())).isFalse();
        assertThat(eval("5 < 10", Map.of())).isTrue();
        assertThat(eval("7 == 7", Map.of())).isTrue();
        assertThat(eval("7 != 7", Map.of())).isFalse();
    }

    @Test
    // 覆盖场景：字符串等值比较（含单引号/双引号剥离）
    void executeShouldCompareStringsAfterStripQuotes() {
        assertThat(eval("{status} == 'done'", Map.of("status", "done"))).isTrue();
        assertThat(eval("{status} == \"doing\"", Map.of("status", "done"))).isFalse();
        assertThat(eval("{status} != 'done'", Map.of("status", "pending"))).isTrue();
    }

    @Test
    // 覆盖场景：非数值比较使用 > 操作符 —— compare 的 default 分支返回 false
    void executeShouldReturnFalseForStringWithGreaterThanOperator() {
        assertThat(eval("'abc' > 'abd'", Map.of())).isFalse();
    }

    @Test
    // 覆盖场景：无操作符时非空即 true；"false"/"0" 特判为 false
    void executeShouldFallbackToNonEmptyTruthiness() {
        assertThat(eval("{flag}", Map.of("flag", "yes"))).isTrue();
        assertThat(eval("false", Map.of())).isFalse();
        assertThat(eval("FALSE", Map.of())).isFalse();
        assertThat(eval("0", Map.of())).isFalse();
        assertThat(eval("{empty}", Map.of())).isFalse();
    }

    @Test
    // 覆盖场景：expression 缺失 / 空串 / config 为 null —— 结果均为 false
    void executeShouldReturnFalseWhenExpressionMissing() {
        assertThat(node.execute(Map.of(), Map.of())).containsEntry("condition_result", false);
        assertThat(eval("", Map.of())).isFalse();
        assertThat(node.execute(Map.of(), null)).containsEntry("condition_result", false);
    }

    @Test
    // 覆盖场景：inputs 为 null —— 变量按空串处理，不抛异常
    void executeShouldTreatNullInputsAsEmptyVariables() {
        Map<String, Object> output = node.execute(null, Map.of("expression", "{x} == ''"));
        assertThat(output).containsEntry("condition_result", true);
    }
}
