package cn.cangjiecloud.tool.consts;

/**
 * 工具相关常量
 */
public final class ToolConstants {

    private ToolConstants() {}

    /**
     * 工具类型枚举
     */
    public static final class ToolType {
        private ToolType() {}

        /** HTTP 调用工具 */
        public static final String HTTP = "HTTP";
        /** 自定义脚本工具（Groovy） */
        public static final String CUSTOM = "CUSTOM";
        /** MCP 协议工具 */
        public static final String MCP = "MCP";
        /** 技能类型工具 */
        public static final String SKILL = "SKILL";
        /** 旧版 Plugin 反射工具 */
        public static final String PLUGIN = "PLUGIN";
        /** 本地工具：服务端不执行，由调用方环境（如浏览器）执行后回传结果 */
        public static final String LOCAL = "LOCAL";
    }

    /** 工具状态 */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
}