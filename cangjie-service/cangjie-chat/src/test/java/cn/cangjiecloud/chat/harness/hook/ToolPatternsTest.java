package cn.cangjiecloud.chat.harness.hook;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolPatterns} 单元测试：覆盖全匹配、前缀通配、后缀通配、
 * 全等匹配与忽略大小写等纯静态匹配逻辑。
 */
class ToolPatternsTest {

    @Nested
    class Matches {

        /** 模式或函数名为空白时不匹配 */
        @Test
        void shouldReturnFalseWhenPatternOrNameBlank() {
            assertThat(ToolPatterns.matches(null, "tool")).isFalse();
            assertThat(ToolPatterns.matches("", "tool")).isFalse();
            assertThat(ToolPatterns.matches("tool", null)).isFalse();
            assertThat(ToolPatterns.matches("tool", "  ")).isFalse();
        }

        /** 单个星号匹配任意函数名 */
        @Test
        void shouldMatchAnythingWithSingleWildcard() {
            assertThat(ToolPatterns.matches("*", "anything")).isTrue();
            assertThat(ToolPatterns.matches(" * ", "x")).isTrue();
        }

        /** 以星号结尾的模式按前缀匹配，忽略大小写 */
        @Test
        void shouldMatchByPrefixIgnoringCase() {
            assertThat(ToolPatterns.matches("delete_*", "delete_user")).isTrue();
            assertThat(ToolPatterns.matches("delete_*", "DELETE_FOLDER")).isTrue();
            assertThat(ToolPatterns.matches("delete_*", "create_user")).isFalse();
        }

        /** 前缀模式比函数名更长时不匹配 */
        @Test
        void shouldNotMatchPrefixWhenNameShorter() {
            assertThat(ToolPatterns.matches("delete_all_*", "delete_")).isFalse();
        }

        /** 以星号开头的模式按后缀匹配，忽略大小写 */
        @Test
        void shouldMatchBySuffixIgnoringCase() {
            assertThat(ToolPatterns.matches("*_export", "data_export")).isTrue();
            assertThat(ToolPatterns.matches("*_export", "DATA_EXPORT")).isTrue();
            assertThat(ToolPatterns.matches("*_export", "data_import")).isFalse();
        }

        /** 后缀模式比函数名更长时不匹配 */
        @Test
        void shouldNotMatchSuffixWhenNameShorter() {
            assertThat(ToolPatterns.matches("*_very_long_suffix", "short")).isFalse();
        }

        /** 无通配符时按全等匹配，忽略大小写 */
        @Test
        void shouldMatchExactNameIgnoringCase() {
            assertThat(ToolPatterns.matches("search", "search")).isTrue();
            assertThat(ToolPatterns.matches("search", "SEARCH")).isTrue();
            assertThat(ToolPatterns.matches("search", "search2")).isFalse();
        }

        /** 模式两端空白会被裁剪后再匹配 */
        @Test
        void shouldTrimPatternBeforeMatching() {
            assertThat(ToolPatterns.matches("  search  ", "search")).isTrue();
        }
    }

    @Nested
    class MatchesAny {

        /** 模式列表为 null/空或函数名空白时返回 false */
        @Test
        void shouldReturnFalseForEmptyInput() {
            assertThat(ToolPatterns.matchesAny(null, "tool")).isFalse();
            assertThat(ToolPatterns.matchesAny(Collections.emptyList(), "tool")).isFalse();
            assertThat(ToolPatterns.matchesAny(List.of("tool"), null)).isFalse();
            assertThat(ToolPatterns.matchesAny(List.of("tool"), " ")).isFalse();
        }

        /** 任一模式命中即返回 true */
        @Test
        void shouldReturnTrueWhenAnyPatternMatches() {
            List<String> patterns = List.of("delete_*", "search", "*_export");

            assertThat(ToolPatterns.matchesAny(patterns, "delete_file")).isTrue();
            assertThat(ToolPatterns.matchesAny(patterns, "SEARCH")).isTrue();
            assertThat(ToolPatterns.matchesAny(patterns, "report_export")).isTrue();
        }

        /** 所有模式都未命中时返回 false */
        @Test
        void shouldReturnFalseWhenNoPatternMatches() {
            assertThat(ToolPatterns.matchesAny(List.of("delete_*", "search"), "create_user")).isFalse();
        }
    }
}
