package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化切片策略
 * <p>
 * 按 Markdown 标题（#/##/###）、段落（双换行）等文档结构切分。
 * 保持语义完整性，适合有清晰层级的文档。
 * 若结构块超过 chunkSize，则进一步按句子细分。
 */
@Component
public class StructuralTextSplitter implements TextSplitter {

    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.trim();
        // 1. 按标题行（# 开头）和双换行分段
        List<Section> sections = extractSections(text);

        // 2. 对每个 section，若长度 <= chunkSize 直接输出，否则按段落聚合
        int chunkIndex = 0;
        for (Section section : sections) {
            if (section.content.length() <= chunkSize) {
                chunks.add(TextChunk.builder()
                        .content(section.content)
                        .source(section.title)
                        .chunkIndex(chunkIndex++)
                        .build());
            } else {
                // 按段落聚合
                List<TextChunk> subChunks = splitByParagraph(section, chunkSize, overlap, chunkIndex);
                chunks.addAll(subChunks);
                chunkIndex += subChunks.size();
            }
        }

        return chunks;
    }

    private List<Section> extractSections(String text) {
        List<Section> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        String currentTitle = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                // 新标题开始，保存上一段
                if (current.length() > 0) {
                    sections.add(new Section(currentTitle, current.toString().trim()));
                }
                currentTitle = trimmed.replaceAll("^#+\\s*", "");
                current = new StringBuilder(line + "\n");
            } else {
                current.append(line).append("\n");
            }
        }
        if (current.length() > 0) {
            sections.add(new Section(currentTitle, current.toString().trim()));
        }
        return sections;
    }

    private List<TextChunk> splitByParagraph(Section section, int chunkSize, int overlap, int startIndex) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] paragraphs = section.content.split("\n\n+");
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = startIndex;

        for (String para : paragraphs) {
            if (buffer.length() + para.length() + 2 > chunkSize && buffer.length() > 0) {
                chunks.add(TextChunk.builder()
                        .content(buffer.toString())
                        .source(section.title)
                        .chunkIndex(chunkIndex++)
                        .build());
                // overlap: 保留尾部
                String tail = buffer.substring(Math.max(0, buffer.length() - overlap));
                buffer = new StringBuilder(tail);
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(para);
        }
        if (buffer.length() > 0) {
            chunks.add(TextChunk.builder()
                    .content(buffer.toString())
                    .source(section.title)
                    .chunkIndex(chunkIndex)
                    .build());
        }
        return chunks;
    }

    private record Section(String title, String content) {}

    @Override
    public SplitStrategy getStrategy() {
        return SplitStrategy.STRUCTURAL;
    }
}
