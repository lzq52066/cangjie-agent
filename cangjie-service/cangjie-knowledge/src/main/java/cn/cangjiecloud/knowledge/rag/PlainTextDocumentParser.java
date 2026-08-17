package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 纯文本/Markdown/HTML 文档解析器
 */
@Component
public class PlainTextDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("文本解析失败: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md")
                || lower.endsWith(".markdown") || lower.endsWith(".csv")
                || lower.endsWith(".html") || lower.endsWith(".htm");
    }
}
