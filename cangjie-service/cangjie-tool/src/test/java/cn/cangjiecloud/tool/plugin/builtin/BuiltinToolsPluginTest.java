package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置本地工具插件测试：哈希（算法别名/非法算法）、UUID（数量收敛/大小写/连字符）、
 * Base64 与 URL 编解码（模式校验/非法输入）、JSON 提取（点路径/JSONPath/缺失路径）。
 */
@DisplayName("内置插件 - BuiltinToolsPlugin")
class BuiltinToolsPluginTest {

    private static final BuiltinToolsPlugin PLUGIN = new BuiltinToolsPlugin();

    private static final String HASH = "builtin_hash";
    private static final String UUID = "builtin_uuid";
    private static final String BASE64 = "builtin_base64";
    private static final String URLCODE = "builtin_urlcode";
    private static final String JSON_EXTRACT = "builtin_json_extract";

    /** 统一走插件公共入口：按 metadata.toolId 分发 */
    private static ObjectNode run(String toolId, Map<String, Object> params) {
        return (ObjectNode) PLUGIN.execute(PluginContext.builder()
                .params(params)
                .metadata(Map.of("toolId", toolId))
                .build());
    }

    private static ObjectNode call(String toolId, Object... kv) {
        return run(toolId, map(kv));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static void assertError(ObjectNode r, String message) {
        assertThat(r.get("success").asBoolean()).isFalse();
        assertThat(r.get("error").asText()).isEqualTo(message);
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
        assertThat(PLUGIN.getName()).isEqualTo("builtin_local_tools");
        assertThat(PLUGIN.getDescription()).isEqualTo("内置本地工具集合：哈希、UUID、Base64、URL 编解码");
        assertThat(PLUGIN.getType()).isEqualTo("tool");
    }

    /**
     * 未注册的 toolId 返回错误提示
     */
    @Test
    @DisplayName("未知 toolId 报错")
    void unknownToolId() {
        ObjectNode r = run("builtin_nope", Map.of());
        assertError(r, "未知的内置工具: builtin_nope");
    }

    // ------------------------------------------------------------------
    // builtin_hash
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("哈希 builtin_hash")
    class Hash {

        /**
         * 默认算法为 md5，输出算法名、原文与摘要
         */
        @Test
        @DisplayName("默认 md5")
        void defaultMd5() {
            ObjectNode r = call(HASH, "text", "hello");
            assertThat(r.get("success").asBoolean()).isTrue();
            assertThat(r.get("algorithm").asText()).isEqualTo("md5");
            assertThat(r.get("text").asText()).isEqualTo("hello");
            assertThat(r.get("hash").asText()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
        }

        /**
         * sha1 与别名 sha-1 等价，输出算法名去掉连字符
         */
        @Test
        @DisplayName("sha1 及别名")
        void sha1() {
            for (String algorithm : new String[]{"sha1", "SHA-1"}) {
                ObjectNode r = call(HASH, "text", "hello", "algorithm", algorithm);
                assertThat(r.get("algorithm").asText()).isEqualTo("sha1");
                assertThat(r.get("hash").asText()).isEqualTo("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d");
            }
        }

        /**
         * sha256 与别名 sha-256 等价，算法名大小写不敏感
         */
        @Test
        @DisplayName("sha256 及别名")
        void sha256() {
            for (String algorithm : new String[]{"sha256", "SHA-256", "SHA256"}) {
                ObjectNode r = call(HASH, "text", "hello", "algorithm", algorithm);
                assertThat(r.get("algorithm").asText()).isEqualTo("sha256");
                assertThat(r.get("hash").asText())
                        .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
            }
        }

        /**
         * 不支持的算法给出可选算法清单
         */
        @Test
        @DisplayName("不支持的算法")
        void unsupportedAlgorithm() {
            ObjectNode r = call(HASH, "text", "hello", "algorithm", "sha512");
            assertError(r, "不支持的算法: sha512（支持 md5 / sha1 / sha256）");
        }

        /**
         * text 缺失或为空白时同一提示（原文不做 trim，仅判空）
         */
        @Test
        @DisplayName("text 为空")
        void emptyText() {
            assertError(run(HASH, Map.of()), "text 不能为空");
            assertError(call(HASH, "text", ""), "text 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // builtin_uuid
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("UUID builtin_uuid")
    class Uuid {

        /**
         * 默认生成 1 个带连字符的小写 UUID，uuid 为 uuids 首元素
         */
        @Test
        @DisplayName("默认生成 1 个")
        void defaults() {
            ObjectNode r = call(UUID);
            assertThat(r.get("success").asBoolean()).isTrue();
            assertThat(r.get("count").asInt()).isEqualTo(1);
            assertThat(r.get("uuids")).hasSize(1);
            assertThat(r.get("uuid").asText())
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(r.get("uuid").asText()).isEqualTo(r.get("uuids").get(0).asText());
        }

        /**
         * count 指定生成数量
         */
        @Test
        @DisplayName("count 指定数量")
        void count() {
            ObjectNode r = call(UUID, "count", 3);
            assertThat(r.get("count").asInt()).isEqualTo(3);
            assertThat(r.get("uuids")).hasSize(3);
        }

        /**
         * count 收敛到 [1, 100]：0/-5 → 1，1000 → 100
         */
        @Test
        @DisplayName("count 越界收敛")
        void countClamp() {
            assertThat(call(UUID, "count", 0).get("count").asInt()).isEqualTo(1);
            assertThat(call(UUID, "count", -5).get("count").asInt()).isEqualTo(1);
            assertThat(call(UUID, "count", 1000).get("count").asInt()).isEqualTo(100);
        }

        /**
         * uppercase=true 输出大写 UUID
         */
        @Test
        @DisplayName("uppercase 大写")
        void uppercase() {
            ObjectNode r = call(UUID, "uppercase", "true");
            assertThat(r.get("uuid").asText())
                    .matches("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
        }

        /**
         * hyphen=false 去掉连字符，长度 32
         */
        @Test
        @DisplayName("hyphen=false 无连字符")
        void noHyphen() {
            ObjectNode r = call(UUID, "uppercase", true, "hyphen", false);
            assertThat(r.get("uuid").asText()).matches("[0-9A-F]{32}");
        }
    }

    // ------------------------------------------------------------------
    // builtin_base64
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Base64 builtin_base64")
    class Base64Codec {

        /**
         * 默认 encode 模式
         */
        @Test
        @DisplayName("默认 encode")
        void encodeDefault() {
            ObjectNode r = call(BASE64, "text", "hello");
            assertThat(r.get("mode").asText()).isEqualTo("encode");
            assertThat(r.get("text").asText()).isEqualTo("hello");
            assertThat(r.get("result").asText()).isEqualTo("aGVsbG8=");
        }

        /**
         * 按 UTF-8 编码中文
         */
        @Test
        @DisplayName("encode 中文")
        void encodeUtf8() {
            ObjectNode r = call(BASE64, "text", "中文", "mode", "ENCODE");
            assertThat(r.get("result").asText()).isEqualTo("5Lit5paH");
        }

        /**
         * decode 模式：忽略输入首尾空白，text 字段仍回传原文
         */
        @Test
        @DisplayName("decode 并去除首尾空白")
        void decode() {
            ObjectNode r = call(BASE64, "text", " aGVsbG8= ", "mode", "decode");
            assertThat(r.get("success").asBoolean()).isTrue();
            assertThat(r.get("text").asText()).isEqualTo(" aGVsbG8= ");
            assertThat(r.get("result").asText()).isEqualTo("hello");
        }

        /**
         * 非法 Base64 输入返回解码失败提示
         */
        @Test
        @DisplayName("decode 非法输入")
        void decodeInvalid() {
            ObjectNode r = call(BASE64, "text", "!!!!", "mode", "decode");
            assertError(r, "Base64 解码失败：输入不是合法的 Base64 内容");
        }

        /**
         * mode 只允许 encode / decode
         */
        @Test
        @DisplayName("非法 mode")
        void invalidMode() {
            assertError(call(BASE64, "text", "hello", "mode", "foo"), "mode 只能是 encode 或 decode");
        }

        /**
         * text 为空时报错
         */
        @Test
        @DisplayName("text 为空")
        void emptyText() {
            assertError(call(BASE64, "text", ""), "text 不能为空");
            assertError(run(BASE64, Map.of()), "text 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // builtin_urlcode
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("URL 编解码 builtin_urlcode")
    class UrlCodec {

        /**
         * 默认 encode：空格转 +，特殊字符与中文按 UTF-8 百分号编码
         */
        @Test
        @DisplayName("默认 encode")
        void encodeDefault() {
            ObjectNode r = call(URLCODE, "text", "a b&c=中");
            assertThat(r.get("mode").asText()).isEqualTo("encode");
            assertThat(r.get("result").asText()).isEqualTo("a+b%26c%3D%E4%B8%AD");
        }

        /**
         * decode 模式：+ 还原为空格
         */
        @Test
        @DisplayName("decode")
        void decode() {
            ObjectNode r = call(URLCODE, "text", "a+b%26c%3D%E4%B8%AD", "mode", "decode");
            assertThat(r.get("result").asText()).isEqualTo("a b&c=中");
        }

        /**
         * 非法转义序列返回带原始异常信息的失败提示
         */
        @Test
        @DisplayName("decode 非法转义")
        void decodeInvalid() {
            ObjectNode r = call(URLCODE, "text", "%zz", "mode", "decode");
            assertThat(r.get("success").asBoolean()).isFalse();
            assertThat(r.get("error").asText()).startsWith("URL decode 失败");
        }

        /**
         * mode 非法与 text 为空的提示
         */
        @Test
        @DisplayName("非法 mode / text 为空")
        void invalidModeAndEmpty() {
            assertError(call(URLCODE, "text", "x", "mode", "foo"), "mode 只能是 encode 或 decode");
            assertError(call(URLCODE, "text", ""), "text 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // builtin_json_extract
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JSON 提取 builtin_json_extract")
    class JsonExtract {

        private static final String JSON = "{\"data\":{\"items\":[{\"name\":\"汤姆\"},{\"name\":\"Jerry\"}],\"n\":7},\"top\":[10,20]}";

        /**
         * 点路径 + 数组下标：data.items.0.name
         */
        @Test
        @DisplayName("点路径取数组元素")
        void dotPath() {
            ObjectNode r = call(JSON_EXTRACT, "json", JSON, "path", "data.items.0.name");
            assertThat(r.get("success").asBoolean()).isTrue();
            assertThat(r.get("found").asBoolean()).isTrue();
            assertThat(r.get("value").asText()).isEqualTo("汤姆");
        }

        /**
         * JSONPath 形式：$.data.items[1].name 与点路径数字下标等价
         */
        @Test
        @DisplayName("JSONPath 取数组元素")
        void jsonPath() {
            assertThat(call(JSON_EXTRACT, "json", JSON, "path", "$.data.items[1].name").get("value").asText())
                    .isEqualTo("Jerry");
            assertThat(call(JSON_EXTRACT, "json", JSON, "path", "data.items.1.name").get("value").asText())
                    .isEqualTo("Jerry");
        }

        /**
         * 引号下标：$['data']['items'][0]['name']
         */
        @Test
        @DisplayName("引号下标路径")
        void quotedIndex() {
            ObjectNode r = call(JSON_EXTRACT, "json", JSON, "path", "$['data']['items'][0]['name']");
            assertThat(r.get("value").asText()).isEqualTo("汤姆");
        }

        /**
         * 根为数组时支持 "$[1]" 与点路径 "1"
         */
        @Test
        @DisplayName("根数组下标")
        void rootArray() {
            String arrayJson = "[10,20]";
            assertThat(call(JSON_EXTRACT, "json", arrayJson, "path", "$[1]").get("value").asInt()).isEqualTo(20);
            assertThat(call(JSON_EXTRACT, "json", arrayJson, "path", "1").get("value").asInt()).isEqualTo(20);
        }

        /**
         * 路径不存在时 found=false 且 value 为 null（不报错）
         */
        @Test
        @DisplayName("路径不存在")
        void notFound() {
            ObjectNode r = call(JSON_EXTRACT, "json", JSON, "path", "data.missing");
            assertThat(r.get("success").asBoolean()).isTrue();
            assertThat(r.get("found").asBoolean()).isFalse();
            assertThat(r.get("value").isNull()).isTrue();
        }

        /**
         * 下标未闭合或非数字/引号 token 时同样按未命中处理
         */
        @Test
        @DisplayName("下标语法缺失")
        void malformedIndex() {
            assertThat(call(JSON_EXTRACT, "json", JSON, "path", "data.items[0").get("found").asBoolean()).isFalse();
            assertThat(call(JSON_EXTRACT, "json", JSON, "path", "data.items[foo]").get("found").asBoolean())
                    .isFalse();
        }

        /**
         * 非法 JSON 返回解析失败提示
         */
        @Test
        @DisplayName("非法 JSON")
        void invalidJson() {
            ObjectNode r = call(JSON_EXTRACT, "json", "not-json", "path", "a");
            assertThat(r.get("success").asBoolean()).isFalse();
            assertThat(r.get("error").asText()).startsWith("JSON 解析或提取失败");
        }

        /**
         * json 与 path 必填
         */
        @Test
        @DisplayName("json / path 为空")
        void emptyInputs() {
            assertError(call(JSON_EXTRACT, "json", "", "path", "a"), "json 不能为空");
            assertError(call(JSON_EXTRACT, "json", JSON, "path", "  "), "path 不能为空");
        }
    }
}
