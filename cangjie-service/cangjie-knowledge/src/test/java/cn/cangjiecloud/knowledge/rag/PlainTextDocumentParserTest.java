package cn.cangjiecloud.knowledge.rag;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PlainTextDocumentParser} 单元测试。
 * <p>
 * 覆盖：UTF-8 文本解析、空流、读取异常包装以及扩展名支持矩阵。
 */
class PlainTextDocumentParserTest {

    private final PlainTextDocumentParser parser = new PlainTextDocumentParser();

    @Test
    void 应按UTF8解析文本内容() {
        byte[] bytes = "第一行\n第二行：中文内容".getBytes(StandardCharsets.UTF_8);

        String text = parser.parse(new ByteArrayInputStream(bytes), "说明.md");

        assertThat(text).isEqualTo("第一行\n第二行：中文内容");
    }

    @Test
    void 空流应返回空字符串() {
        assertThat(parser.parse(new ByteArrayInputStream(new byte[0]), "empty.txt")).isEmpty();
    }

    @Test
    void 读取失败应包装为RuntimeException() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("磁盘读取失败");
            }
        };

        assertThatThrownBy(() -> parser.parse(failing, "损坏文件.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文本解析失败: 损坏文件.txt")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void supports应识别全部文本类扩展名() {
        assertThat(parser.supports("a.txt")).isTrue();
        assertThat(parser.supports("b.md")).isTrue();
        assertThat(parser.supports("c.markdown")).isTrue();
        assertThat(parser.supports("d.csv")).isTrue();
        assertThat(parser.supports("e.html")).isTrue();
        assertThat(parser.supports("f.htm")).isTrue();
        assertThat(parser.supports("G.TXT")).isTrue();
    }

    @Test
    void supports应拒绝二进制扩展名与null() {
        assertThat(parser.supports(null)).isFalse();
        assertThat(parser.supports("报告.docx")).isFalse();
        assertThat(parser.supports("报告.pdf")).isFalse();
        assertThat(parser.supports("README")).isFalse();
    }
}
