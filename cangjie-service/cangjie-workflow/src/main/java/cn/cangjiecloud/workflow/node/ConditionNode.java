package cn.cangjiecloud.workflow.node;

import cn.cangjiecloud.core.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件节点 — 评估表达式，返回条件结果供执行引擎路由
 * <p>
 * config 参数：
 * - expression: 条件表达式，支持简单比较：
 *   - 变量引用: {variableName} 会被替换为上下文值
 *   - 比较: ==, !=, >, <, >=, <=
 *   - 示例: "{score} >= 80", "{status} == 'done'", "{llm_output} != ''"
 * <p>
 * 输出:
 * - condition_result: true / false
 */
@Slf4j
@Component
public class ConditionNode implements WorkflowNode {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    @Override
    public String getType() {
        return "condition";
    }

    @Override
    public String getName() {
        return "条件";
    }

    @Override
    public String getDescription() {
        return "根据条件表达式选择执行分支";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, Map<String, Object> config) {
        Map<String, Object> safeConfig = config != null ? config : Map.of();
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();

        String expression = (String) safeConfig.get("expression");
        boolean result = false;

        if (expression != null && !expression.isEmpty()) {
            // 替换变量占位符
            String resolved = resolveVariables(expression, safeInputs);
            result = evaluateSimple(resolved);
        }

        log.info("ConditionNode: expression='{}', result={}", expression, result);

        Map<String, Object> output = new HashMap<>();
        output.put("condition_result", result);
        return output;
    }

    /**
     * 替换 {variable} 为实际值
     */
    private String resolveVariables(String template, Map<String, Object> variables) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 简单表达式求值：支持 ==, !=, >, <, >=, <=
     */
    private boolean evaluateSimple(String expr) {
        expr = expr.trim();

        String[] operators = {">=", "<=", "!=", "==", ">", "<"};
        for (String op : operators) {
            int idx = expr.indexOf(op);
            if (idx > 0) {
                String left = expr.substring(0, idx).trim();
                String right = expr.substring(idx + op.length()).trim();
                // 去除引号
                left = stripQuotes(left);
                right = stripQuotes(right);
                return compare(left, right, op);
            }
        }

        // 没有操作符，非空即 true
        return !expr.isEmpty() && !"false".equalsIgnoreCase(expr) && !"0".equals(expr);
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private boolean compare(String left, String right, String op) {
        // 尝试数值比较
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default -> false;
            };
        } catch (NumberFormatException ignored) {
            // 字符串比较
        }
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> false;
        };
    }
}
