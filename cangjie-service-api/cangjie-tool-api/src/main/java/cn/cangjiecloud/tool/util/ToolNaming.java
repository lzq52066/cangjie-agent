package cn.cangjiecloud.tool.util;

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
}