package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 图片文档解析器
 * <p>
 * 支持 .png、.jpg、.jpeg、.gif、.bmp、.webp 格式。
 * 提取图片的元数据信息（尺寸、格式等），作为文本内容存入知识库。
 * <p>
 * 注意：当前不支持 OCR 文字识别，仅提取图片元数据。
 * 如需 OCR 能力，可后续集成 Tesseract 或云端 OCR 服务。
 */
@Slf4j
@Component
public class ImageDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try {
            // 使用 ImageIO 读取图片元数据
            try (ImageInputStream iis = ImageIO.createImageInputStream(inputStream)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) {
                    throw new RuntimeException("无法识别图片格式: " + fileName);
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    String formatName = reader.getFormatName();

                    StringBuilder sb = new StringBuilder();
                    sb.append("图片文件: ").append(fileName).append("\n");
                    sb.append("格式: ").append(formatName != null ? formatName.toUpperCase() : "未知").append("\n");
                    sb.append("尺寸: ").append(width).append(" x ").append(height).append(" 像素\n");
                    sb.append("文件大小: ").append(inputStream instanceof java.io.ByteArrayInputStream bis
                            ? formatFileSize(bis.available()) : "未知");

                    log.debug("图片解析: {} → {}x{} {}", fileName, width, height, formatName);
                    return sb.toString();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("图片解析失败: " + fileName, e);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }
}
