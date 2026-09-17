package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TextChunk} 单元测试，覆盖可空 Integer 包装类型边界。
 */
class TextChunkTest {

    @Test
    void builderShouldPopulateAllFields() {
        TextChunk chunk = TextChunk.builder()
                .content("切片内容")
                .source("doc.md")
                .pageNumber(2)
                .startOffset(120)
                .chunkIndex(0)
                .build();

        assertThat(chunk.getContent()).isEqualTo("切片内容");
        assertThat(chunk.getSource()).isEqualTo("doc.md");
        assertThat(chunk.getPageNumber()).isEqualTo(2);
        assertThat(chunk.getStartOffset()).isEqualTo(120);
        assertThat(chunk.getChunkIndex()).isZero();
    }

    @Test
    void emptyBuilderShouldLeaveBoxedIntegersNullNotZero() {
        TextChunk chunk = TextChunk.builder().build();

        assertThat(chunk.getContent()).isNull();
        assertThat(chunk.getSource()).isNull();
        assertThat(chunk.getPageNumber()).isNull();
        assertThat(chunk.getStartOffset()).isNull();
        assertThat(chunk.getChunkIndex()).isNull();
    }

    @Test
    void noArgsConstructorAndSettersShouldRoundTrip() {
        TextChunk chunk = new TextChunk();
        chunk.setContent("c");
        chunk.setSource("s");
        chunk.setPageNumber(null);
        chunk.setStartOffset(-1);
        chunk.setChunkIndex(Integer.MAX_VALUE);

        assertThat(chunk.getContent()).isEqualTo("c");
        assertThat(chunk.getSource()).isEqualTo("s");
        assertThat(chunk.getPageNumber()).isNull();
        assertThat(chunk.getStartOffset()).isEqualTo(-1);
        assertThat(chunk.getChunkIndex()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void allArgsConstructorShouldFollowFieldOrder() {
        TextChunk chunk = new TextChunk("c", "s", 1, 2, 3);

        assertThat(chunk.getContent()).isEqualTo("c");
        assertThat(chunk.getSource()).isEqualTo("s");
        assertThat(chunk.getPageNumber()).isEqualTo(1);
        assertThat(chunk.getStartOffset()).isEqualTo(2);
        assertThat(chunk.getChunkIndex()).isEqualTo(3);
    }

    @Test
    void equalsShouldDistinguishNullFromZeroIndex() {
        TextChunk nullIndex = TextChunk.builder().content("x").build();
        TextChunk zeroIndex = TextChunk.builder().content("x").chunkIndex(0).build();

        assertThat(nullIndex).isNotEqualTo(zeroIndex);
        assertThat(zeroIndex).isEqualTo(TextChunk.builder().content("x").chunkIndex(0).build());
        assertThat(zeroIndex).hasSameHashCodeAs(TextChunk.builder().content("x").chunkIndex(0).build());
        assertThat(zeroIndex).isNotEqualTo(null);
        assertThat(zeroIndex).isNotEqualTo("x");
    }

    @Test
    void equalsShouldConsiderPageNumberAndOffset() {
        TextChunk a = new TextChunk("c", "s", 1, 1, 1);
        assertThat(a).isNotEqualTo(new TextChunk("c", "s", 2, 1, 1));
        assertThat(a).isNotEqualTo(new TextChunk("c", "s", 1, 5, 1));
    }

    @Test
    void toStringShouldContainAllFields() {
        String text = TextChunk.builder().content("body").source("a.pdf").pageNumber(7)
                .startOffset(3).chunkIndex(1).build().toString();

        assertThat(text).contains("content=body").contains("source=a.pdf").contains("pageNumber=7")
                .contains("startOffset=3").contains("chunkIndex=1");
    }
}
