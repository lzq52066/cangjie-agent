package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档解析器（基于 Apache POI）
 * <p>
 * 支持 .docx（XWPF）和 .doc（HWPF）两种格式。
 * 按文档 body 元素顺序提取段落和表格内容。
 */
@Slf4j
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        if (fileName != null && fileName.toLowerCase().endsWith(".doc")) {
            return parseDoc(inputStream, fileName);
        }
        return parseDocx(inputStream, fileName);
    }

    /**
     * 解析 .docx 格式（XWPF）
     */
    private String parseDocx(InputStream inputStream, String fileName) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            List<String> parts = new ArrayList<>();

            doc.getBodyElements().forEach(element -> {
                if (element instanceof XWPFParagraph para) {
                    String text = para.getText();
                    if (text != null && !text.isBlank()) {
                        parts.add(text.trim());
                    }
                } else if (element instanceof XWPFTable table) {
                    String tableText = extractTableText(table);
                    if (!tableText.isBlank()) {
                        parts.add(tableText);
                    }
                }
            });

            String result = String.join("\n\n", parts);
            log.debug("Word(.docx) 文档解析: {} → {} 个元素, {} 字符", fileName, parts.size(), result.length());
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Word 文档解析失败: " + fileName, e);
        }
    }

    /**
     * 解析 .doc 格式（HWPF）
     */
    private String parseDoc(InputStream inputStream, String fileName) {
        try (HWPFDocument doc = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(doc)) {
            String[] paragraphs = extractor.getParagraphText();
            List<String> parts = new ArrayList<>();
            for (String para : paragraphs) {
                if (para != null && !para.isBlank()) {
                    parts.add(para.trim());
                }
            }
            String result = String.join("\n\n", parts);
            log.debug("Word(.doc) 文档解析: {} → {} 段, {} 字符", fileName, parts.size(), result.length());
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Word(.doc) 文档解析失败: " + fileName, e);
        }
    }

    /**
     * 提取表格内容：每行用制表符分隔单元格，行间用换行分隔
     */
    private String extractTableText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (cellText != null && !cellText.isBlank()) {
                    cells.add(cellText.trim());
                }
            }
            if (!cells.isEmpty()) {
                rows.add(String.join("\t", cells));
            }
        }
        return String.join("\n", rows);
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }
}
