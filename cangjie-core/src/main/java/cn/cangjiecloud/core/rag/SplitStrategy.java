package cn.cangjiecloud.core.rag;

/**
 * 文本切片策略枚举
 */
public enum SplitStrategy {

    /** 智能分段：三阶段流水线（标题切分 → 超长段落句子级切分 → 清洗+短段合并），零配置 */
    SMART("smart", "智能分段"),

    /** 自定义分段：用户指定分隔符和段落最大长度 */
    CUSTOM("custom", "自定义分段");

    private final String code;
    private final String label;

    SplitStrategy(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SplitStrategy of(String code) {
        if (code == null || code.isBlank()) return SMART;
        for (SplitStrategy s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        // 兼容数字编码（早期版本可能存储了序号）
        if ("1".equals(code)) return SMART;
        if ("2".equals(code)) return CUSTOM;
        // 兼容旧值：sentence/structural/token → smart
        if ("sentence".equalsIgnoreCase(code) || "structural".equalsIgnoreCase(code)
                || "token".equalsIgnoreCase(code)) {
            return SMART;
        }
        return SMART;
    }
}
