package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能分段策略
 * <p>
 * 三阶段流水线（参考 MaxKB4j）：
 * 1. 按 Markdown 标题递归切分（h1~h6）
 * 2. 超长段落（>512字符）按句子切分，保护表格
 * 3. 清洗 + 短段合并
 * <p>
 * 零配置，无需用户指定 chunkSize 和 overlap。
 */
@Component
public class SmartTextSplitter implements TextSplitter {

    /** 默认段落最大字符数 */
    private static final int DEFAULT_LIMIT = 512;

    /** Markdown 标题正则（h1~h6） */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE
    );

    /** 句子边界正则（中英文标点） */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "[。！？!?;；]+|\\n+"
    );

    /** 表格行检测（以 | 开头和结尾） */
    private static final Pattern TABLE_ROW = Pattern.compile(
            "^\\|.*\\|$"
    );

    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        // chunkSize 和 overlap 参数被忽略，使用内部默认值
        return smartSplit(text);
    }

    /**
     * 智能分段主入口
     */
    public List<TextChunk> smartSplit(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.trim();

        // 阶段1：按标题切分
        List<Section> sections = splitByHeadings(text);

        // 阶段2：超长段落按句子切分（保护表格）
        List<Section> splitParts = new ArrayList<>();
        for (Section section : sections) {
            if (section.content.length() <= DEFAULT_LIMIT) {
                splitParts.add(section);
            } else {
                splitParts.addAll(splitContentPreserveTable(section, DEFAULT_LIMIT));
            }
        }

        // 阶段3：清洗 + 短段合并
        int chunkIndex = 0;
        StringBuilder buffer = new StringBuilder();
        String currentTitle = "";

        for (Section part : splitParts) {
            String cleaned = cleanAndFilter(part.content);
            List<String> lines = lineSplit(cleaned, DEFAULT_LIMIT);

            for (String line : lines) {
                if (line.isBlank()) continue;

                if (buffer.length() + line.length() > DEFAULT_LIMIT && buffer.length() > 0) {
                    // 输出当前切片
                    chunks.add(TextChunk.builder()
                            .content(buffer.toString().trim())
                            .source(currentTitle)
                            .chunkIndex(chunkIndex++)
                            .build());
                    buffer = new StringBuilder();
                    currentTitle = part.title;
                }

                if (buffer.length() > 0) {
                    buffer.append("\n");
                }
                buffer.append(line);
            }
        }

        // 最后剩余内容
        if (buffer.length() > 0) {
            chunks.add(TextChunk.builder()
                    .content(buffer.toString().trim())
                    .source(currentTitle)
                    .chunkIndex(chunkIndex)
                    .build());
        }

        // 安全兜底：非空输入必须产出至少一个 chunk
        if (chunks.isEmpty() && text != null && !text.isBlank()) {
            chunks.add(TextChunk.builder()
                    .content(text.trim())
                    .source("")
                    .chunkIndex(0)
                    .build());
        }

        return chunks;
    }

    /**
     * 阶段1：按 Markdown 标题递归切分
     */
    private List<Section> splitByHeadings(String text) {
        List<Section> sections = new ArrayList<>();

        // 检查是否有标题
        if (!text.contains("\n#") && !text.startsWith("#")) {
            sections.add(new Section("", text));
            return sections;
        }

        Matcher matcher = HEADING_PATTERN.matcher(text);
        int lastEnd = 0;
        String currentTitle = "";
        StringBuilder currentContent = new StringBuilder();

        while (matcher.find()) {
            // 保存标题前的内容（如第一个标题之前的前言文本）
            if (currentContent.length() > 0) {
                sections.add(new Section(currentTitle, currentContent.toString().trim()));
            } else if (matcher.start() > 0 && sections.isEmpty()) {
                // 第一个标题之前有文本，作为独立 section 保留
                String preamble = text.substring(0, matcher.start()).trim();
                if (!preamble.isEmpty()) {
                    sections.add(new Section("", preamble));
                }
            }

            // 开始新段
            currentTitle = matcher.group(2).trim();
            currentContent = new StringBuilder();
            lastEnd = matcher.end();
        }

        // 剩余内容
        if (lastEnd < text.length()) {
            currentContent.append(text.substring(lastEnd));
        }
        if (currentContent.length() > 0) {
            sections.add(new Section(currentTitle, currentContent.toString().trim()));
        }

        return sections;
    }

    /**
     * 阶段2：保留表格的切分
     */
    private List<Section> splitContentPreserveTable(Section section, int limit) {
        List<Section> result = new ArrayList<>();
        String[] lines = section.content.split("\n");

        StringBuilder tableBlock = new StringBuilder();
        StringBuilder textBlock = new StringBuilder();
        boolean inTable = false;

        for (String line : lines) {
            if (TABLE_ROW.matcher(line.trim()).matches()) {
                // 表格行
                if (textBlock.length() > 0) {
                    // 先处理之前的文本
                    result.addAll(splitBySentences(
                            new Section(section.title, textBlock.toString().trim()), limit));
                    textBlock = new StringBuilder();
                }
                inTable = true;
                tableBlock.append(line).append("\n");
            } else {
                // 非表格行
                if (inTable) {
                    // 表格结束，整体保留
                    if (tableBlock.length() <= limit) {
                        result.add(new Section(section.title, tableBlock.toString().trim()));
                    } else {
                        // 超长表格按行切分
                        result.addAll(splitByLines(
                                new Section(section.title, tableBlock.toString().trim()), limit));
                    }
                    tableBlock = new StringBuilder();
                    inTable = false;
                }
                textBlock.append(line).append("\n");
            }
        }

        // 处理剩余内容
        if (tableBlock.length() > 0) {
            if (tableBlock.length() <= limit) {
                result.add(new Section(section.title, tableBlock.toString().trim()));
            } else {
                result.addAll(splitByLines(
                        new Section(section.title, tableBlock.toString().trim()), limit));
            }
        }
        if (textBlock.length() > 0) {
            result.addAll(splitBySentences(
                    new Section(section.title, textBlock.toString().trim()), limit));
        }

        return result;
    }

    /**
     * 按句子边界切分
     */
    private List<Section> splitBySentences(Section section, int limit) {
        List<Section> result = new ArrayList<>();
        String text = section.content;

        if (text.length() <= limit) {
            result.add(section);
            return result;
        }

        // 按句子边界切分
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_BOUNDARY.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            String sentence = text.substring(lastEnd, matcher.end()).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                sentences.add(remaining);
            }
        }

        // 合并句子到不超过 limit 的段落
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() > limit && buffer.length() > 0) {
                result.add(new Section(section.title, buffer.toString().trim()));
                buffer = new StringBuilder();
            }
            if (buffer.length() > 0) {
                buffer.append(" ");
            }
            buffer.append(sentence);
        }

        if (buffer.length() > 0) {
            result.add(new Section(section.title, buffer.toString().trim()));
        }

        return result;
    }

    /**
     * 按行切分（用于超长表格）
     */
    private List<Section> splitByLines(Section section, int limit) {
        List<Section> result = new ArrayList<>();
        String[] lines = section.content.split("\n");
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            if (buffer.length() + line.length() > limit && buffer.length() > 0) {
                result.add(new Section(section.title, buffer.toString().trim()));
                buffer = new StringBuilder();
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(line);
        }

        if (buffer.length() > 0) {
            result.add(new Section(section.title, buffer.toString().trim()));
        }

        return result;
    }

    /**
     * 阶段3：清洗文本
     * - 合并多余空格
     * - 合并多余空行
     * - 移除 Markdown 标题符号
     */
    private String cleanAndFilter(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 移除 Markdown 标题符号
        text = text.replaceAll("^#+\\s*", "");

        // 合并多余空格（保留换行）
        text = text.replaceAll("[ \\t]+", " ");

        // 合并多余空行（最多保留一个空行）
        text = text.replaceAll("\n{3,}", "\n\n");

        return text.trim();
    }

    /**
     * 按行切分并合并到 limit 以内
     */
    private List<String> lineSplit(String text, int limit) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        String[] lines = text.split("\n");
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            if (buffer.length() + line.length() > limit && buffer.length() > 0) {
                result.add(buffer.toString().trim());
                buffer = new StringBuilder();
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(line);
        }

        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    @Override
    public SplitStrategy getStrategy() {
        return SplitStrategy.SMART;
    }

    /**
     * 文档节（标题 + 内容）
     */
    private record Section(String title, String content) {}
}
