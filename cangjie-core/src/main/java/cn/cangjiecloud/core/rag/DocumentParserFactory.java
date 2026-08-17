package cn.cangjiecloud.core.rag;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档解析器工厂
 * <p>
 * 自动收集所有 {@link DocumentParser} 实现，根据文件名后缀选择对应解析器。
 */
@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;
    private final Map<String, DocumentParser> cache = new ConcurrentHashMap<>();

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 根据文件名获取对应的文档解析器
     */
    public DocumentParser get(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return cache.computeIfAbsent(fileName.toLowerCase(), fn -> {
            for (DocumentParser parser : parsers) {
                if (parser.supports(fn)) {
                    return parser;
                }
            }
            throw new IllegalArgumentException("不支持的文件格式: " + fn);
        });
    }

    /**
     * 解析文档
     */
    public String parse(InputStream inputStream, String fileName) {
        return get(fileName).parse(inputStream, fileName);
    }
}
