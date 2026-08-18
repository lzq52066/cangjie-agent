package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PPT 文档解析器（基于 Apache POI）
 * <p>
 * 支持 .pptx 格式（XSLF）。
 * 逐页提取文本内容，每页作为一个段落块。
 */
@Slf4j
@Component
public class PptDocumentParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream, String fileName) {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            List<String> parts = new ArrayList<>();
            List<XSLFSlide> slides = ppt.getSlides();

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                List<String> texts = new ArrayList<>();

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text.trim());
                        }
                    }
                }

                if (!texts.isEmpty()) {
                    parts.add("第" + (i + 1) + "页");
                    parts.addAll(texts);
                }
            }

            String result = String.join("\n", parts);
            log.debug("PPT 文档解析: {} → {} 页, {} 字符", fileName, slides.size(), result.length());
            return result;
        } catch (IOException e) {
            throw new RuntimeException("PPT 文档解析失败: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pptx");
    }
}
