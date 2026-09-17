package cn.cangjiecloud.chat.harness.hook;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 函数名模式匹配：策略配置（allow/deny/需审批函数）支持通配，避免逐个枚举工具名。
 * <p>
 * 规则：{@code *} 匹配全部；{@code xxx_*} 前缀匹配；其余按函数名全等（忽略大小写）。
 */
public final class ToolPatterns {

    private ToolPatterns() {
    }

    public static boolean matchesAny(List<String> patterns, String callName) {
        if (patterns == null || patterns.isEmpty() || !StringUtils.hasText(callName)) {
            return false;
        }
        String name = callName.trim();
        for (String pattern : patterns) {
            if (matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String pattern, String callName) {
        if (!StringUtils.hasText(pattern) || !StringUtils.hasText(callName)) {
            return false;
        }
        String rule = pattern.trim();
        if ("*".equals(rule)) {
            return true;
        }
        if (rule.endsWith("*")) {
            return callName.regionMatches(true, 0, rule, 0, rule.length() - 1);
        }
        if (rule.startsWith("*")) {
            String suffix = rule.substring(1);
            return callName.length() >= suffix.length()
                    && callName.regionMatches(true, callName.length() - suffix.length(), suffix, 0, suffix.length());
        }
        return callName.equalsIgnoreCase(rule);
    }
}
