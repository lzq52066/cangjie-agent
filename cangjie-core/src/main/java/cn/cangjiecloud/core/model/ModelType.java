package cn.cangjiecloud.core.model;

/**
 * 模型类型枚举
 */
public enum ModelType {

    OPENAI("openai", "OpenAI"),
    DEEPSEEK("deepseek", "DeepSeek"),
    QWEN("qwen", "通义千问"),
    ZHIPU("zhipu", "智谱清言"),
    WENXIN("wenxin", "文心一言"),
    OLLAMA("ollama", "Ollama 本地模型"),
    CUSTOM("custom", "自定义");

    private final String code;
    private final String label;

    ModelType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ModelType of(String code) {
        if (code == null) return OPENAI;
        for (ModelType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return CUSTOM;
    }
}
