package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 自定义分段策略
 * <p>
 * 用户指定分隔符（标题/空行/句号/逗号等）和段落最大长度，
 * 按分隔符切分后合并到 chunkSize 以内，支持 overlap。
 */
@Component
public class CustomSeparatorTextSplitter implements TextSplitter {

    /** 预定义分隔符映射 */
    private static final Map<String, Pattern> SEPARATOR_PATTERNS = new HashMap<>();

    static {
        SEPARATOR_PATTERNS.put("h1", Pattern.compile("(?=^# )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("h2", Pattern.compile("(?=^## )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("h3", Pattern.compile("(?=^### )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("h4", Pattern.compile("(?=^#### )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("h5", Pattern.compile("(?=^##### )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("h6", Pattern.compile("(?=^###### )", Pattern.MULTILINE));
        SEPARATOR_PATTERNS.put("blank_line", Pattern.compile("\\n\\s*\\n"));
        SEPARATOR_PATTERNS.put("period", Pattern.compile("(?<=[。！？!?])"));
        SEPARATOR_PATTERNS.put("semicolon", Pattern.compile("(?<=[;；])"));
        SEPARATOR_PATTERNS.put("comma", Pattern.compile("(?<=[,，])"));
        SEPARATOR_PATTERNS.put("newline", Pattern.compile("\\n"));
    }

    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        // 默认使用 blank_line 分隔符
        return splitWithSeparators(text, chunkSize, overlap, List.of("blank_line"));
    }

    /**
     * 使用指定分隔符切分
     *
     * @param text       原始文本
     * @param chunkSize  段落最大字符数
     * @param overlap    相邻切片重叠字符数
     * @param separators 分隔符名称列表（如 ["h2", "blank_line"]）
     * @return 切片列表
     */
    public List<TextChunk> splitWithSeparators(String text, int chunkSize, int overlap,
                                                List<String> separators) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.trim();

        // 按分隔符切分为片段
        List<String> fragments = splitBySeparators(text, separators);

        // 合并片段到 chunkSize 以内
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        int bufferStart = 0;

        for (String fragment : fragments) {
            if (fragment.isBlank()) continue;

            if (buffer.length() + fragment.length() > chunkSize && buffer.length() > 0) {
                // 输出当前切片
                chunks.add(TextChunk.builder()
                        .content(buffer.toString().trim())
                        .chunkIndex(chunkIndex++)
                        .startOffset(bufferStart)
                        .build());

                // 处理 overlap
                if (overlap > 0 && buffer.length() > overlap) {
                    String tail = buffer.substring(buffer.length() - overlap);
                    bufferStart = bufferStart + buffer.length() - overlap;
                    buffer = new StringBuilder(tail);
                } else {
                    buffer = new StringBuilder();
                    bufferStart = bufferStart + buffer.length();
                }
            }

            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(fragment);
        }

        // 最后剩余内容
        if (buffer.length() > 0) {
            chunks.add(TextChunk.builder()
                    .content(buffer.toString().trim())
                    .chunkIndex(chunkIndex)
                    .startOffset(bufferStart)
                    .build());
        }

        return chunks;
    }

    /**
     * 按分隔符切分文本
     */
    private List<String> splitBySeparators(String text, List<String> separators) {
        List<String> fragments = new ArrayList<>();

        if (separators == null || separators.isEmpty()) {
            fragments.add(text);
            return fragments;
        }

        // 使用第一个分隔符切分
        String firstSep = separators.get(0);
        Pattern pattern = SEPARATOR_PATTERNS.get(firstSep);

        if (pattern == null) {
            // 未知分隔符，直接返回原文
            fragments.add(text);
            return fragments;
        }

        String[] parts = pattern.split(text);
        List<String> remainingSeps = separators.size() > 1
                ? separators.subList(1, separators.size())
                : List.of();

        for (String part : parts) {
            if (part.isBlank()) continue;

            // 如果片段仍然很长且有更多分隔符，递归切分
            if (part.length() > 1000 && !remainingSeps.isEmpty()) {
                fragments.addAll(splitBySeparators(part, remainingSeps));
            } else {
                fragments.add(part.trim());
            }
        }

        return fragments;
    }

    /**
     * 获取所有可用的分隔符
     */
    public static Map<String, Pattern> getAvailableSeparators() {
        return SEPARATOR_PATTERNS;
    }

    @Override
    public SplitStrategy getStrategy() {
        return SplitStrategy.CUSTOM;
    }
}
