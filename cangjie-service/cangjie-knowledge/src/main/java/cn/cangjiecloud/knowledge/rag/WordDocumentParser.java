package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * Word 文档解析器（基于 Apache POI）
 */
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n\n"));
        } catch (IOException e) {
            throw new RuntimeException("Word 文档解析失败: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }
}
