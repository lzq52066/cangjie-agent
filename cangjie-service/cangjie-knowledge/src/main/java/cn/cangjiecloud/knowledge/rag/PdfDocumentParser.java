package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 文档解析器（基于 Apache PDFBox 3.x）
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setAddMoreFormatting(true);
                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 解析失败: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf");
    }
}
