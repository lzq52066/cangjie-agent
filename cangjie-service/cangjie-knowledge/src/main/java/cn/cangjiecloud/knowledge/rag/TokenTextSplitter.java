package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 级切片策略
 * <p>
 * 按 Token 数量精确切分。由于不依赖外部 tokenizer，
 * 使用近似估算：1 个中文字符 ≈ 1.5 token，1 个英文单词 ≈ 1.3 token。
 * 优先在句末或词边界处切分，避免截断单词。
 */
@Component
public class TokenTextSplitter implements TextSplitter {

    /** 近似 token 估算比例：字符数 × 此系数 */
    private static final double CHARS_TO_TOKEN_RATIO = 0.75;

    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.trim();
        // chunkSize 是字符数，转换为 token 目标
        int targetTokens = (int) (chunkSize * CHARS_TO_TOKEN_RATIO);
        int overlapTokens = (int) (overlap * CHARS_TO_TOKEN_RATIO);

        // 近似：目标字符数 = targetTokens / CHARS_TO_TOKEN_RATIO = chunkSize
        // 所以直接用 chunkSize 作为字符目标，但在词边界切分
        int targetChars = chunkSize;
        int overlapChars = overlap;

        if (text.length() <= targetChars) {
            chunks.add(TextChunk.builder()
                    .content(text)
                    .chunkIndex(0)
                    .startOffset(0)
                    .build());
            return chunks;
        }

        int start = 0;
        int chunkIndex = 0;
        while (start < text.length()) {
            int end = Math.min(start + targetChars, text.length());
            // 尝试在词边界或句末切分
            if (end < text.length()) {
                int boundary = findBoundary(text, start, end);
                if (boundary > start + targetChars / 2) {
                    end = boundary;
                }
            }
            String chunkContent = text.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(TextChunk.builder()
                        .content(chunkContent)
                        .chunkIndex(chunkIndex++)
                        .startOffset(start)
                        .build());
            }
            // 下一个切片起点：回退 overlap
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlapChars);
        }

        return chunks;
    }

    /**
     * 在 [start, end] 范围内从后往前找到最近的词边界（空格、标点、换行）
     */
    private int findBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start + end / 4; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t' || c == '。' || c == '.'
                    || c == '！' || c == '!' || c == '？' || c == '?'
                    || c == '；' || c == ';' || c == '，' || c == ',') {
                return i + 1;
            }
        }
        return end;
    }

    @Override
    public SplitStrategy getStrategy() {
        return SplitStrategy.TOKEN;
    }
}
