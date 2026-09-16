package cn.cangjiecloud.tool.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 工具命名工具类
 * 统一管理 function calling 中的工具命名规范
 */
public final class ToolNaming {

    private ToolNaming() {}

    private static final String TOOL_PREFIX = "tool_";
    private static final String AGENT_PREFIX = "agent_";
    private static final String KNOWLEDGE_PREFIX = "knowledge_";
    public static final String SKILL_PREFIX = "skill_";

    /** 平台保留前缀：用户自定义函数名不得使用，否则会与内置路由规则冲突 */
    private static final List<String> RESERVED_PREFIXES =
            List.of(TOOL_PREFIX, AGENT_PREFIX, KNOWLEDGE_PREFIX, SKILL_PREFIX);

    /** 模型侧函数名字符集约束（OpenAI / Anthropic 兼容） */
    private static final Pattern FUNCTION_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");

    /** 平台内置的固定函数名：这些名字在分发时被提前拦截，工具不得占用 */
    private static final List<String> RESERVED_NAMES = List.of("search_knowledge_base");

    /** 构建工具 function calling 名称 */
    public static String buildToolName(String toolId) {
        return TOOL_PREFIX + toolId;
    }

    /** 构建 Agent 工具名称 */
    public static String buildAgentName(String agentId) {
        return AGENT_PREFIX + agentId;
    }

    /** 构建知识库工具名称 */
    public static String buildKnowledgeName(String kbId) {
        return KNOWLEDGE_PREFIX + kbId;
    }

    /** 构建技能工具名称 */
    public static String buildSkillName(String skillId) {
        return SKILL_PREFIX + skillId;
    }

    /** 解析工具名称，返回原始 ID */
    public static String parse(String toolName) {
        if (toolName == null) return null;
        if (toolName.startsWith(TOOL_PREFIX)) return toolName.substring(TOOL_PREFIX.length());
        if (toolName.startsWith(AGENT_PREFIX)) return toolName.substring(AGENT_PREFIX.length());
        if (toolName.startsWith(KNOWLEDGE_PREFIX)) return toolName.substring(KNOWLEDGE_PREFIX.length());
        if (toolName.startsWith(SKILL_PREFIX)) return toolName.substring(SKILL_PREFIX.length());
        return toolName;
    }

    /** 函数名是否合法（可直接暴露给模型） */
    public static boolean isValidFunctionName(String name) {
        return name != null && FUNCTION_NAME_PATTERN.matcher(name).matches();
    }

    /** 函数名是否与平台内置路由规则冲突（保留前缀或保留全名） */
    public static boolean isReservedFunctionName(String name) {
        if (name == null) return false;
        if (RESERVED_NAMES.contains(name)) return true;
        for (String prefix : RESERVED_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /** 函数名归一化：去除首尾空白，空串返回 null */
    public static String normalizeFunctionName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 解析 function calling 中实际暴露的函数名：
     * 工具自定义了合法且非保留的 functionName 时使用它，否则回退到 tool_&lt;id&gt;。
     */
    public static String resolveCallName(String functionName, String toolId) {
        String name = normalizeFunctionName(functionName);
        if (name != null && isValidFunctionName(name) && !isReservedFunctionName(name)) {
            return name;
        }
        return buildToolName(toolId);
    }
}