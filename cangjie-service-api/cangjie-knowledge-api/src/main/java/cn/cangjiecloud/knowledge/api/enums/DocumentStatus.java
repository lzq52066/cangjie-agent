package cn.cangjiecloud.knowledge.api.enums;

/**
 * 文档处理状态
 */
public enum DocumentStatus {

    PENDING("pending", "待处理"),
    PARSING("parsing", "解析中"),
    SPLITTING("splitting", "切片中"),
    EMBEDDING("embedding", "向量化中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "处理失败");

    private final String code;
    private final String label;

    DocumentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
