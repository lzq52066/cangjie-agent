package cn.cangjiecloud.core.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentParserFactory} 单元测试：后缀匹配、大小写、缓存、空/非法入参与解析委托。
 */
class DocumentParserFactoryTest {

    private DocumentParser parserFor(String extension, String parsedText) {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.supports(anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).endsWith(extension));
        when(parser.parse(any(InputStream.class), anyString())).thenReturn(parsedText);
        return parser;
    }

    private DocumentParser strictParser(String extension) {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.supports(anyString())).thenAnswer(invocation -> ((String) invocation.getArgument(0))
                .toLowerCase().endsWith(extension));
        return parser;
    }

    @Test
    void getShouldPickParserSupportingFileExtension() {
        DocumentParser pdf = parserFor(".pdf", "pdf-text");
        DocumentParser md = parserFor(".md", "md-text");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf, md));

        assertThat(factory.get("report.pdf")).isSameAs(pdf);
        assertThat(factory.get("readme.md")).isSameAs(md);
    }

    @Test
    void getShouldMatchFileNameCaseInsensitively() {
        DocumentParser pdf = strictParser(".pdf");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        // 传入前会被 toLowerCase，因此解析器看到的是小写文件名
        assertThat(factory.get("REPORT.PDF")).isSameAs(pdf);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(pdf).supports(captor.capture());
        assertThat(captor.getValue()).isEqualTo("report.pdf");
    }

    @Test
    void getShouldThrowForNullOrEmptyFileName() {
        DocumentParserFactory factory = new DocumentParserFactory(List.of(parserFor(".pdf", "x")));

        assertThatThrownBy(() -> factory.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件名不能为空");
        assertThatThrownBy(() -> factory.get(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件名不能为空");
        assertThatThrownBy(() -> factory.get("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件名不能为空");
    }

    @Test
    void getShouldThrowWhenNoParserSupportsFormat() {
        DocumentParser pdf = parserFor(".pdf", "x");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        assertThatThrownBy(() -> factory.get("archive.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件格式")
                .hasMessageContaining("archive.zip");
    }

    @Test
    void getShouldThrowForUnsupportedFormatWhenParserListIsEmpty() {
        DocumentParserFactory factory = new DocumentParserFactory(List.of());

        assertThatThrownBy(() -> factory.get("a.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getShouldCacheResolvedParserPerFileName() {
        DocumentParser pdf = strictParser(".pdf");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        DocumentParser first = factory.get("a.pdf");
        DocumentParser second = factory.get("a.pdf");

        assertThat(first).isSameAs(second);
        // 命中缓存后不再调用 supports
        verify(pdf, times(1)).supports("a.pdf");
    }

    @Test
    void cacheKeyShouldBeCaseInsensitiveSoSameFileHitsSameEntry() {
        DocumentParser pdf = strictParser(".pdf");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        factory.get("A.PDF");
        factory.get("a.pdf");

        verify(pdf, times(1)).supports("a.pdf");
    }

    @Test
    void differentFileNamesShouldEachResolveSeparately() {
        DocumentParser pdf = strictParser(".pdf");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        factory.get("a.pdf");
        factory.get("b.pdf");

        verify(pdf).supports("a.pdf");
        verify(pdf).supports("b.pdf");
    }

    @Test
    void failedLookupShouldNotBeCachedAndShouldRetryMatching() {
        DocumentParser pdf = strictParser(".pdf");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        assertThatThrownBy(() -> factory.get("x.zip")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.get("x.zip")).isInstanceOf(IllegalArgumentException.class);

        verify(pdf, times(2)).supports("x.zip");
    }

    @Test
    void parseShouldDelegateToResolvedParser() {
        DocumentParser md = parserFor(".md", "markdown-body");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(md, parserFor(".pdf", "pdf-body")));
        InputStream stream = new ByteArrayInputStream("# hi".getBytes(StandardCharsets.UTF_8));

        String parsed = factory.parse(stream, "notes.md");

        assertThat(parsed).isEqualTo("markdown-body");
        verify(md).parse(stream, "notes.md");
    }

    @Test
    void parseShouldPropagateNullFileNameGuard() {
        DocumentParser pdf = parserFor(".pdf", "x");
        DocumentParserFactory factory = new DocumentParserFactory(List.of(pdf));

        assertThatThrownBy(() -> factory.parse(new ByteArrayInputStream(new byte[0]), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件名不能为空");
        verify(pdf, never()).parse(any(), anyString());
    }

    @Test
    void parseShouldPropagateParserException() {
        DocumentParser broken = mock(DocumentParser.class);
        when(broken.supports(anyString())).thenAnswer(invocation -> ((String) invocation.getArgument(0))
                .endsWith(".doc"));
        when(broken.parse(any(), anyString())).thenThrow(new RuntimeException("corrupted"));
        DocumentParserFactory factory = new DocumentParserFactory(List.of(broken));

        assertThatThrownBy(() -> factory.parse(new ByteArrayInputStream(new byte[0]), "file.doc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("corrupted");
    }

    @Test
    void nullParserListShouldFailFastAndBreakLaterLookup() {
        DocumentParserFactory factory = new DocumentParserFactory(null);

        assertThatThrownBy(() -> factory.get("a.pdf")).isInstanceOf(NullPointerException.class);
    }
}
