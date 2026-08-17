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
 * 句子级切片策略
 * <p>
 * 先按标点符号（中英文句号、问号、感叹号、换行）断句，
 * 再将句子聚合到接近 chunkSize 的大小，相邻切片保留 overlap 的重叠内容。
 */
@Component
public class SentenceTextSplitter implements TextSplitter {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
            "[^。！？!?.\\n]+[。！？!?.\\n]*"
    );

    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.trim();
        if (text.length() <= chunkSize) {
            chunks.add(TextChunk.builder()
                    .content(text)
                    .chunkIndex(0)
                    .startOffset(0)
                    .build());
            return chunks;
        }

        // 1. 断句
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            String sentence = matcher.group().trim();
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

        // 2. 聚合
        StringBuilder buffer = new StringBuilder();
        int bufferStart = 0;
        int chunkIndex = 0;
        int i = 0;

        while (i < sentences.size()) {
            String sentence = sentences.get(i);
            if (buffer.length() + sentence.length() > chunkSize && buffer.length() > 0) {
                // 输出当前切片
                chunks.add(TextChunk.builder()
                        .content(buffer.toString())
                        .chunkIndex(chunkIndex++)
                        .startOffset(bufferStart)
                        .build());

                // 保留 overlap：从 buffer 尾部取 overlap 字符作为下一个切片的起始
                String tail = buffer.substring(Math.max(0, buffer.length() - overlap));
                buffer = new StringBuilder(tail);
                bufferStart = bufferStart + buffer.length() - tail.length();
            }
            buffer.append(sentence);
            i++;
        }

        // 最后剩余内容
        if (buffer.length() > 0) {
            chunks.add(TextChunk.builder()
                    .content(buffer.toString())
                    .chunkIndex(chunkIndex)
                    .startOffset(bufferStart)
                    .build());
        }

        return chunks;
    }

    @Override
    public SplitStrategy getStrategy() {
        return SplitStrategy.SENTENCE;
    }
}
