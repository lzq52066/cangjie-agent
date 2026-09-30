package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmartTextSplitter} 单元测试。
 * <p>
 * 覆盖：空输入、Markdown 标题切分、超长段落句子切分、表格保护、文本清洗与兜底逻辑。
 */
class SmartTextSplitterTest {

    /** 14 个字符的中文句子，便于精确计算合并后的长度 */
    private static final String SENTENCE = "这是用于测试长度的中文句子。";

    private SmartTextSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new SmartTextSplitter();
    }

    private static String sentences(int count) {
        return SENTENCE.repeat(count);
    }

    @Nested
    class 基础行为 {

        @Test
        void nullOrBlankInputShouldReturnEmptyList() {
            assertThat(splitter.smartSplit(null)).isEmpty();
            assertThat(splitter.smartSplit("")).isEmpty();
            assertThat(splitter.smartSplit("   \n\t ")).isEmpty();
        }

        @Test
        void getStrategyShouldReturnSmart() {
            assertThat(splitter.getStrategy()).isEqualTo(SplitStrategy.SMART);
        }

        @Test
        void splitShouldIgnoreChunkSizeAndOverlapParameters() {
            String text = "普通文本内容，没有标题。";

            List<TextChunk> byInterface = splitter.split(text, 10, 5);
            List<TextChunk> bySmart = splitter.smartSplit(text);

            assertThat(byInterface).hasSameSizeAs(bySmart);
            assertThat(byInterface.get(0).getContent()).isEqualTo(bySmart.get(0).getContent());
        }
    }

    @Nested
    class 标题切分 {

        @Test
        void plainTextWithoutHeadingShouldProduceSingleChunk() {
            List<TextChunk> chunks = splitter.smartSplit("第一行\n第二行");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("第一行\n第二行");
            assertThat(chunks.get(0).getSource()).isEmpty();
            assertThat(chunks.get(0).getChunkIndex()).isZero();
        }

        @Test
        void singleHeadingShouldProduceChunkWithHeadingAsSource() {
            List<TextChunk> chunks = splitter.smartSplit("# 标题\n正文内容");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("正文内容");
            assertThat(chunks.get(0).getSource()).isEqualTo("标题");
        }

        @Test
        void contentBeforeFirstHeadingShouldBeKeptAsPreambleSection() {
            List<TextChunk> chunks = splitter.smartSplit("前言段落\n\n# 标题\n内容");

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).getContent()).isEqualTo("前言段落");
            assertThat(chunks.get(0).getSource()).isEmpty();
            assertThat(chunks.get(1).getContent()).isEqualTo("内容");
            assertThat(chunks.get(1).getSource()).isEqualTo("标题");
        }

        @Test
        void twoHeadingsShouldMergeFirstHeadingIntoPreambleSection() {
            // 当前实现：第一个标题及其内容被并入"前言"Section（source 为空），
            // 仅最后一个标题的正文保留其标题作为 source。
            List<TextChunk> chunks = splitter.smartSplit("# 标题一\n\n内容一\n\n# 标题二\n\n内容二");

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).getContent()).isEqualTo("标题一\n\n内容一");
            assertThat(chunks.get(0).getSource()).isEmpty();
            assertThat(chunks.get(1).getContent()).isEqualTo("内容二");
            assertThat(chunks.get(1).getSource()).isEqualTo("标题二");
        }

        @Test
        void threeHeadingsCurrentlyDropMiddleSectionContent() {
            // 记录当前实现的行为：仅在 sections 为空时才捕获"标题前文本"，
            // 导致第 2~n-1 个标题之间的正文（内容B）被整体丢弃。
            List<TextChunk> chunks = splitter.smartSplit("# A\n内容A\n# B\n内容B\n# C\n内容C");

            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).getContent()).isEqualTo("A\n内容A");
            assertThat(chunks.get(1).getContent()).isEqualTo("内容C");
            assertThat(chunks).noneMatch(c -> c.getContent().contains("内容B"));
        }
    }

    @Nested
    class 超长段落切分 {

        @Test
        void longTextWithoutSentenceBoundaryShouldFallbackToWholeChunk() {
            String text = "a".repeat(600);

            List<TextChunk> chunks = splitter.smartSplit(text);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).hasSize(600);
        }

        @Test
        void longSentenceTextShouldSplitIntoChunksWithinLimit() {
            String text = sentences(40); // 560 字符 > 512

            List<TextChunk> chunks = splitter.smartSplit(text);

            assertThat(chunks).hasSize(2);
            assertThat(chunks).allMatch(c -> c.getContent().length() <= 512);
            // 句子完整保留：句号总数不变
            int periods = chunks.stream().mapToInt(c -> c.getContent().length() - c.getContent().replace("。", "").length()).sum();
            assertThat(periods).isEqualTo(40);
            assertThat(chunks).extracting(TextChunk::getChunkIndex).containsExactly(0, 1);
        }
    }

    @Nested
    class 表格保护 {

        @Test
        void tableShouldBePreservedAsWholeChunk() {
            String paragraph = sentences(40);
            String table = "| 列1 | 列2 |\n| --- | --- |\n| 值1 | 值2 |";
            String text = paragraph + "\n" + table + "\n尾部说明。";

            List<TextChunk> chunks = splitter.smartSplit(text);

            assertThat(chunks).hasSize(4);
            // 表格整体保留为一个 chunk，且不与正文混合
            List<TextChunk> tableChunks = chunks.stream()
                    .filter(c -> c.getContent().contains("列1"))
                    .toList();
            assertThat(tableChunks).hasSize(1);
            assertThat(tableChunks.get(0).getContent()).isEqualTo(table);
            assertThat(tableChunks.get(0).getContent()).contains("| 值1 | 值2 |");
        }

        @Test
        void oversizeTableShouldBeSplitByLinesKeepingRowIntegrity() {
            String row = "| aaaaaaaaaa | bbbbbbbbbb |";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 25; i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(row);
            }

            List<TextChunk> chunks = splitter.smartSplit(sb.toString());

            assertThat(chunks).hasSize(2);
            assertThat(chunks).allMatch(c -> c.getContent().length() <= 512);
            Pattern tableRow = Pattern.compile("^\\|.*\\|$");
            int rows = chunks.stream()
                    .flatMap(c -> List.of(c.getContent().split("\n")).stream())
                    .peek(line -> assertThat(tableRow.matcher(line).matches()).isTrue())
                    .toList().size();
            assertThat(rows).isEqualTo(25);
        }
    }

    @Nested
    class 文本清洗 {

        @Test
        void cleaningShouldCollapseExtraSpacesAndBlankLines() {
            List<TextChunk> chunks = splitter.smartSplit("A  B   C\n\n\n\nD");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("A B C\n\nD");
        }
    }
}
