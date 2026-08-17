package cn.cangjiecloud.core.rag;

/**
 * 文本切片策略枚举
 */
public enum SplitStrategy {

    /** 句子级切片：按标点符号断句后聚合到目标大小 */
    SENTENCE("sentence", "句子切片"),

    /** 结构化切片：按标题/段落/换行等文档结构切分 */
    STRUCTURAL("structural", "结构切片"),

    /** Token 级切片：按 Token 数量精确切分，适合严格长度控制 */
    TOKEN("token", "Token切片");

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
        if (code == null || code.isBlank()) return SENTENCE;
        for (SplitStrategy s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        return SENTENCE;
    }
}
