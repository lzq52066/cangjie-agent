package cn.cangjiecloud.knowledge.api.enums;

/**
 * 文档类型
 */
public enum DocumentType {

    PDF("pdf"),
    WORD("word"),
    MARKDOWN("markdown"),
    TEXT("text"),
    HTML("html"),
    CSV("csv"),
    EXCEL("excel");

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static DocumentType of(String fileName) {
        if (fileName == null) return TEXT;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return PDF;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return WORD;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return MARKDOWN;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return HTML;
        if (lower.endsWith(".csv")) return CSV;
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return EXCEL;
        return TEXT;
    }
}
