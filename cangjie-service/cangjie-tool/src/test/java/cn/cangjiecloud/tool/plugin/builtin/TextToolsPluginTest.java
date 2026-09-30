package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置文本插件测试：字数统计（中英混排/换行/emoji/超限判定）与随机生成
 * （区间整数与小数、随机串、强口令的边界与非法参数）。
 */
@DisplayName("内置插件 - TextToolsPlugin")
class TextToolsPluginTest {

    private static final TextToolsPlugin PLUGIN = new TextToolsPlugin();

    private static final String STATS = "builtin_text_stats";
    private static final String RANDOM = "builtin_random";

    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/";

    /** 统一走插件公共入口：按 metadata.toolId 分发 */
    private static ObjectNode run(String toolId, Map<String, Object> params) {
        return (ObjectNode) PLUGIN.execute(PluginContext.builder()
                .params(params)
                .metadata(Map.of("toolId", toolId))
                .build());
    }

    private static ObjectNode stats(Object... kv) {
        return run(STATS, map(kv));
    }

    private static ObjectNode random(Object... kv) {
        return run(RANDOM, map(kv));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------
    // 元信息 / 分发
    // ------------------------------------------------------------------

    /**
     * 插件元信息固定
     */
    @Test
    @DisplayName("插件元信息")
    void metadata() {
        assertThat(PLUGIN.getName()).isEqualTo("builtin_text_tools");
        assertThat(PLUGIN.getDescription())
                .isEqualTo("内置文本工具集合：文本/字数统计、随机数与随机口令生成");
        assertThat(PLUGIN.getType()).isEqualTo("tool");
    }

    /**
     * 未注册的 toolId 返回错误提示
     */
    @Test
    @DisplayName("未知 toolId 报错")
    void unknownToolId() {
        ObjectNode r = run("builtin_nope", Map.of());
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: builtin_nope");
    }

    /**
     * context 为 null 时返回错误而非抛异常
     */
    @Test
    @DisplayName("context 为 null 不抛异常")
    void nullContext() {
        ObjectNode r = (ObjectNode) PLUGIN.execute(null);
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: ");
    }

    // ------------------------------------------------------------------
    // builtin_text_stats
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("文本统计 builtin_text_stats")
    class TextStats {

        /**
         * 中英混排：汉字按字、英文按词，分类计数与 UTF-8 字节数逐一核对
         */
        @Test
        @DisplayName("中英混排文本分类计数")
        void mixedChineseAndEnglish() {
            ObjectNode r = stats("text", "你好世界 Hello World! 42");

            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("characters").asInt()).isEqualTo(20);
            assertThat(r.path("charactersNoSpaces").asInt()).isEqualTo(17);
            assertThat(r.path("chineseCharacters").asInt()).isEqualTo(4);
            assertThat(r.path("letters").asInt()).isEqualTo(10);
            assertThat(r.path("digits").asInt()).isEqualTo(2);
            assertThat(r.path("punctuation").asInt()).isEqualTo(1);
            assertThat(r.path("whitespace").asInt()).isEqualTo(3);
            assertThat(r.path("latinWords").asInt()).isEqualTo(2);
            // 字数口径：4 个汉字 + 2 个英文单词
            assertThat(r.path("words").asInt()).isEqualTo(6);
            assertThat(r.path("numbers").asInt()).isEqualTo(1);
            assertThat(r.path("lines").asInt()).isEqualTo(1);
            assertThat(r.path("utf8Bytes").asInt()).isEqualTo(28);
        }

        /**
         * 多行文本：CRLF/LF 混合按行拆分，换行符计入空白
         */
        @Test
        @DisplayName("多行文本行数统计")
        void multiLine() {
            ObjectNode r = stats("text", "a\r\nbb\nc");

            assertThat(r.path("characters").asInt()).isEqualTo(7);
            assertThat(r.path("whitespace").asInt()).isEqualTo(3);
            assertThat(r.path("letters").asInt()).isEqualTo(4);
            assertThat(r.path("lines").asInt()).isEqualTo(3);
        }

        /**
         * emoji 代理对按 2 个字符计入 characters，但不误判为标点
         */
        @Test
        @DisplayName("emoji 代理对计数")
        void emojiSurrogate() {
            ObjectNode r = stats("text", "😀");

            assertThat(r.path("characters").asInt()).isEqualTo(2);
            assertThat(r.path("punctuation").asInt()).isZero();
            assertThat(r.path("letters").asInt()).isZero();
            assertThat(r.path("whitespace").asInt()).isZero();
            assertThat(r.path("utf8Bytes").asInt()).isEqualTo(4);
        }

        /**
         * 正文原样统计：首尾空格不计为缺失，且计入字符与空白
         */
        @Test
        @DisplayName("首尾空格原样统计")
        void textNotTrimmed() {
            ObjectNode r = stats("text", "  hi  ");

            assertThat(r.path("characters").asInt()).isEqualTo(6);
            assertThat(r.path("charactersNoSpaces").asInt()).isEqualTo(2);
            assertThat(r.path("whitespace").asInt()).isEqualTo(4);
            assertThat(r.path("letters").asInt()).isEqualTo(2);

            // 纯空白也视为有内容
            assertThat(stats("text", "   ").path("success").asBoolean()).isTrue();
        }

        /**
         * maxChars 正数时给出是否超限与超出多少；数字字符串同样生效
         */
        @Test
        @DisplayName("maxChars 超限判定")
        void maxCharsLimit() {
            ObjectNode exceeded = stats("text", "abcde", "maxChars", 3);
            assertThat(exceeded.path("maxChars").asInt()).isEqualTo(3);
            assertThat(exceeded.path("withinLimit").asBoolean()).isFalse();
            assertThat(exceeded.path("exceededBy").asInt()).isEqualTo(2);

            ObjectNode within = stats("text", "abcde", "maxChars", "5");
            assertThat(within.path("withinLimit").asBoolean()).isTrue();
            assertThat(within.path("exceededBy").asInt()).isZero();
        }

        /**
         * maxChars 缺省 / 非正数 / 非法值时不做超限判定
         */
        @Test
        @DisplayName("maxChars 无效时省略判定字段")
        void maxCharsIgnored() {
            assertThat(stats("text", "abcde").has("maxChars")).isFalse();
            assertThat(stats("text", "abcde", "maxChars", 0).has("maxChars")).isFalse();
            assertThat(stats("text", "abcde", "maxChars", "many").has("maxChars")).isFalse();
        }

        /**
         * text 必填：缺省或空串都提示参数为空
         */
        @Test
        @DisplayName("text 必填")
        void textRequired() {
            assertThat(stats().path("error").asText()).isEqualTo("参数 text 不能为空");
            assertThat(stats("text", "").path("error").asText()).isEqualTo("参数 text 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // builtin_random
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("随机生成 builtin_random")
    class Random {

        /**
         * 默认区间整数：min 0 / max 100 / count 1
         */
        @Test
        @DisplayName("区间整数默认参数")
        void randomIntDefaults() {
            ObjectNode r = random();

            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("type").asText()).isEqualTo("int");
            assertThat(r.path("min").asLong()).isZero();
            assertThat(r.path("max").asLong()).isEqualTo(100);
            assertThat(r.path("count").asInt()).isEqualTo(1);
            assertThat(r.path("value").asLong()).isBetween(0L, 100L);
            assertThat(r.path("values")).hasSize(1);
        }

        /**
         * 字符串化的 min/max/count 兼容；min&gt;max 自动交换；min=max 恒定输出
         */
        @Test
        @DisplayName("区间整数边界与自动交换")
        void randomIntBounds() {
            ObjectNode swapped = random("min", "10", "max", "5", "count", "3");

            assertThat(swapped.path("min").asLong()).isEqualTo(5);
            assertThat(swapped.path("max").asLong()).isEqualTo(10);
            assertThat(swapped.path("count").asInt()).isEqualTo(3);
            assertThat(swapped.path("values")).hasSize(3);
            for (JsonNode v : swapped.path("values")) {
                assertThat(v.asLong()).isBetween(5L, 10L);
            }

            ObjectNode fixed = random("min", 7, "max", 7);
            assertThat(fixed.path("value").asLong()).isEqualTo(7);
        }

        /**
         * count 收敛到 [1, 200]
         */
        @Test
        @DisplayName("count 上下界收敛")
        void withCountClamped() {
            assertThat(random("count", 0).path("count").asInt()).isEqualTo(1);
            assertThat(random("count", "-5").path("count").asInt()).isEqualTo(1);

            ObjectNode many = random("min", 1, "max", 2, "count", 300);
            assertThat(many.path("count").asInt()).isEqualTo(200);
            assertThat(many.path("values")).hasSize(200);
        }

        /**
         * 区间小数：precision 控制输出精度，precision=0 时输出整数形态
         */
        @Test
        @DisplayName("区间小数精度与范围")
        void randomDecimal() {
            ObjectNode r = random("type", "decimal", "count", 5);

            assertThat(r.path("type").asText()).isEqualTo("decimal");
            assertThat(r.path("min").asDouble()).isZero();
            assertThat(r.path("max").asDouble()).isEqualTo(1);
            assertThat(r.path("values")).hasSize(5);
            for (JsonNode v : r.path("values")) {
                assertThat(v.asDouble()).isBetween(0.0, 1.0);
            }

            ObjectNode integral = random("type", "double", "min", 0, "max", 10, "precision", 0, "count", 3);
            for (JsonNode v : integral.path("values")) {
                assertThat(v.isIntegralNumber()).isTrue();
            }
        }

        /**
         * 区间小数 min&gt;max 交换后按新范围输出
         */
        @Test
        @DisplayName("小数区间交换")
        void randomDecimalSwap() {
            ObjectNode r = random("type", "decimal", "min", 2.5, "max", 0.5, "count", 2);

            assertThat(r.path("min").asDouble()).isEqualTo(0.5);
            assertThat(r.path("max").asDouble()).isEqualTo(2.5);
            for (JsonNode v : r.path("values")) {
                assertThat(v.asDouble()).isBetween(0.5, 2.5);
            }
        }

        /**
         * 随机串：只启用数字时串内仅含数字，长度与条数正确
         */
        @Test
        @DisplayName("随机串字符集与长度")
        void randomStringDigitsOnly() {
            ObjectNode r = random("type", "string", "lowercase", false, "uppercase", false,
                    "length", 8, "count", 2);

            assertThat(r.path("type").asText()).isEqualTo("string");
            assertThat(r.path("length").asInt()).isEqualTo(8);
            assertThat(r.path("values")).hasSize(2);
            for (JsonNode v : r.path("values")) {
                assertThat(v.asText()).hasSize(8).matches("[0-9]+");
            }
        }

        /**
         * 随机串 length 收敛到 [1, 512]
         */
        @Test
        @DisplayName("随机串长度收敛")
        void randomStringLengthClamped() {
            assertThat(random("type", "string", "length", 0).path("length").asInt()).isEqualTo(1);
            assertThat(random("type", "string", "length", "1000").path("length").asInt()).isEqualTo(512);
            assertThat(random("type", "string", "length", 1000).path("value").asText()).hasSize(512);
        }

        /**
         * noAmbiguous=true 时剔除易混淆的 l/o 与 0/1
         */
        @Test
        @DisplayName("随机串 noAmbiguous 剔除混淆字符")
        void randomStringNoAmbiguous() {
            ObjectNode lower = random("type", "string", "noAmbiguous", true, "uppercase", false,
                    "digits", false, "length", 64);
            assertThat(lower.path("value").asText()).matches("[a-km-np-z]+");

            ObjectNode digits = random("type", "string", "noAmbiguous", true, "lowercase", false,
                    "uppercase", false, "length", 32);
            assertThat(digits.path("value").asText()).matches("[2-9]+");
        }

        /**
         * 随机串三类字符全关闭时报错
         */
        @Test
        @DisplayName("随机串字符集全关报错")
        void randomStringNoPool() {
            ObjectNode r = random("type", "string", "lowercase", false, "uppercase", false, "digits", false);

            assertThat(r.path("error").asText())
                    .isEqualTo("string 类型至少需要启用 lowercase / uppercase / digits 之一");
        }

        /**
         * 强口令：默认 4 类字符集，每类至少出现一次，强度按熵估算
         */
        @Test
        @DisplayName("强口令包含全部字符类别")
        void randomPasswordCategories() {
            ObjectNode r = random("type", "password", "count", 2);

            assertThat(r.path("type").asText()).isEqualTo("password");
            assertThat(r.path("length").asInt()).isEqualTo(16);
            // 26+26+10+24=86 字符集、长度 16 → 约 103 bit
            assertThat(r.path("strength").asText()).isEqualTo("强（熵约 103 bit）");
            for (JsonNode v : r.path("values")) {
                String password = v.asText();
                assertThat(password).hasSize(16);
                assertThat(password.chars().anyMatch(Character::isLowerCase)).isTrue();
                assertThat(password.chars().anyMatch(Character::isUpperCase)).isTrue();
                assertThat(password.chars().anyMatch(Character::isDigit)).isTrue();
                assertThat(password.chars().anyMatch(c -> SYMBOLS.indexOf(c) >= 0)).isTrue();
            }
        }

        /**
         * 口令长度下限为 4；只启用数字时按 10 字符集给出弱强度提示
         */
        @Test
        @DisplayName("口令长度收敛与弱强度")
        void randomPasswordShortAndWeak() {
            ObjectNode r = random("type", "password", "length", 2, "lowercase", false,
                    "uppercase", false, "symbols", false);

            assertThat(r.path("length").asInt()).isEqualTo(4);
            assertThat(r.path("values")).hasSize(1);
            assertThat(r.path("values").get(0).asText()).matches("[0-9]{4}");
            assertThat(r.path("strength").asText())
                    .isEqualTo("弱（熵约 13 bit，建议增加长度或启用更多字符类别）");
        }

        /**
         * 中等长度口令给出中强度提示
         */
        @Test
        @DisplayName("口令中强度")
        void randomPasswordMediumStrength() {
            ObjectNode r = random("type", "pwd", "length", 8);

            assertThat(r.path("strength").asText()).isEqualTo("中（熵约 51 bit）");
        }

        /**
         * 口令四类字符全关闭时报错
         */
        @Test
        @DisplayName("口令字符集全关报错")
        void randomPasswordNoPool() {
            ObjectNode r = random("type", "password", "lowercase", false, "uppercase", false,
                    "digits", false, "symbols", false);

            assertThat(r.path("error").asText())
                    .isEqualTo("password 至少需要启用 lowercase / uppercase / digits / symbols 之一");
        }

        /**
         * type 别名：integer/number→int、double→decimal、alnum/text→string、pwd/secret→password
         */
        @Test
        @DisplayName("type 别名归一化")
        void typeAliases() {
            assertThat(random("type", "integer").path("type").asText()).isEqualTo("int");
            assertThat(random("type", "number").path("type").asText()).isEqualTo("int");
            assertThat(random("type", "float").path("type").asText()).isEqualTo("decimal");
            assertThat(random("type", "alnum").path("type").asText()).isEqualTo("string");
            assertThat(random("type", "text", "length", 4).path("type").asText()).isEqualTo("string");
            assertThat(random("type", "pwd").path("type").asText()).isEqualTo("password");
            assertThat(random("type", "SECRET").path("type").asText()).isEqualTo("password");
        }

        /**
         * 未知 type 报错并列出可选项
         */
        @Test
        @DisplayName("未知 type 报错")
        void unknownType() {
            ObjectNode r = random("type", "boolean");

            assertThat(r.path("success").asBoolean()).isFalse();
            assertThat(r.path("error").asText())
                    .isEqualTo("不支持的 type: boolean（可选 int / decimal / string / password）");
        }
    }
}
