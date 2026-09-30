package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置解析插件测试：URL 拆解（协议/主机/端口/路径/查询/锚点/凭据）与 JWT 解码
 * （分段校验、Base64URL/JSON 非法输入、时间声明与时区）。
 */
@DisplayName("内置插件 - ParserToolsPlugin")
class ParserToolsPluginTest {

    private static final ParserToolsPlugin PLUGIN = new ParserToolsPlugin();

    private static final String URL_PARSE = "builtin_url_parse";
    private static final String JWT_DECODE = "builtin_jwt_decode";

    /** 统一走插件公共入口：按 metadata.toolId 分发 */
    private static ObjectNode run(String toolId, Map<String, Object> params) {
        return (ObjectNode) PLUGIN.execute(PluginContext.builder()
                .params(params)
                .metadata(Map.of("toolId", toolId))
                .build());
    }

    private static ObjectNode url(Object... kv) {
        return run(URL_PARSE, map(kv));
    }

    private static ObjectNode jwt(Object... kv) {
        return run(JWT_DECODE, map(kv));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** Base64URL（无填充）编码 JSON 片段，用于拼装 JWT */
    private static String seg(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
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
        assertThat(PLUGIN.getName()).isEqualTo("builtin_parser_tools");
        assertThat(PLUGIN.getDescription()).isEqualTo("内置解析工具集合：URL 拆解、JWT 解码");
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
     * context 为 null 时走默认分支返回错误而非抛异常
     */
    @Test
    @DisplayName("context 为 null 不抛异常")
    void nullContext() {
        ObjectNode r = (ObjectNode) PLUGIN.execute(null);
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").asText()).isEqualTo("未知的内置工具: ");
    }

    // ------------------------------------------------------------------
    // builtin_url_parse
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("URL 拆解 builtin_url_parse")
    class UrlParse {

        /**
         * 完整 URL：协议、凭据、主机、显式端口、路径、重复查询参数、锚点与 origin
         */
        @Test
        @DisplayName("完整 URL 拆出全部字段")
        void fullUrl() {
            ObjectNode r = url("url", "https://user:secret@www.example.com:8443/a/b?x=1&x=2&y=%E4%B8%AD&z#frag");

            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("scheme").asText()).isEqualTo("https");
            assertThat(r.path("host").asText()).isEqualTo("www.example.com");
            assertThat(r.path("port").asInt()).isEqualTo(8443);
            assertThat(r.path("explicitPort").asInt()).isEqualTo(8443);
            assertThat(r.path("path").asText()).isEqualTo("/a/b");
            assertThat(r.path("query").asText()).isEqualTo("x=1&x=2&y=%E4%B8%AD&z");
            assertThat(r.path("fragment").asText()).isEqualTo("frag");
            assertThat(r.path("origin").asText()).isEqualTo("https://www.example.com:8443");
            // 同名查询键合并为数组，无等号的键取空串
            assertThat(r.path("queryParams").path("x").isArray()).isTrue();
            assertThat(r.path("queryParams").path("x").get(0).asText()).isEqualTo("1");
            assertThat(r.path("queryParams").path("x").get(1).asText()).isEqualTo("2");
            assertThat(r.path("queryParams").path("y").asText()).isEqualTo("中");
            assertThat(r.path("queryParams").path("z").asText()).isEmpty();
            // 只回传用户名与是否携带口令，不回传口令原文
            assertThat(r.path("username").asText()).isEqualTo("user");
            assertThat(r.path("hasPassword").asBoolean()).isTrue();
        }

        /**
         * 用户名不带口令时 hasPassword=false
         */
        @Test
        @DisplayName("仅有用户名时 hasPassword=false")
        void userWithoutPassword() {
            ObjectNode r = url("url", "https://justuser@example.com/");

            assertThat(r.path("username").asText()).isEqualTo("justuser");
            assertThat(r.path("hasPassword").asBoolean()).isFalse();
        }

        /**
         * 各协议在未写端口时给出默认端口，explicitPort 为 null
         */
        @Test
        @DisplayName("默认端口与 explicitPort")
        void defaultPorts() {
            assertThat(url("url", "http://example.com/p").path("port").asInt()).isEqualTo(80);
            assertThat(url("url", "ws://example.com/chat").path("port").asInt()).isEqualTo(80);
            assertThat(url("url", "https://example.com").path("port").asInt()).isEqualTo(443);
            assertThat(url("url", "wss://example.com").path("port").asInt()).isEqualTo(443);
            assertThat(url("url", "ftp://example.com/f").path("port").asInt()).isEqualTo(21);
            assertThat(url("url", "gopher://example.com").path("port").asInt()).isEqualTo(-1);
            ObjectNode r = url("url", "http://example.com");
            assertThat(r.has("explicitPort")).isTrue();
            assertThat(r.path("explicitPort").isNull()).isTrue();
        }

        /**
         * 缺少协议时补 https，且容忍模型包裹的尖括号/引号与首尾空格
         */
        @Test
        @DisplayName("无协议补 https 并剥离包裹符号")
        void missingSchemeAndWrappers() {
            ObjectNode r = url("url", "  <www.example.com/a>  ");
            assertThat(r.path("url").asText()).isEqualTo("https://www.example.com/a");
            assertThat(r.path("scheme").asText()).isEqualTo("https");
            assertThat(r.path("host").asText()).isEqualTo("www.example.com");
            assertThat(r.path("port").asInt()).isEqualTo(443);

            assertThat(url("url", "'example.com/x'").path("host").asText()).isEqualTo("example.com");
        }

        /**
         * 协议与主机名统一小写，路径保留大小写
         */
        @Test
        @DisplayName("协议/主机归一化小写，路径保留原样")
        void schemeAndHostLowercased() {
            ObjectNode r = url("url", "HTTP://Example.COM/Path?A=B");

            assertThat(r.path("scheme").asText()).isEqualTo("http");
            assertThat(r.path("host").asText()).isEqualTo("example.com");
            assertThat(r.path("path").asText()).isEqualTo("/Path");
            assertThat(r.path("queryParams").path("A").asText()).isEqualTo("B");
        }

        /**
         * mailto:/tel:/file: 等不透明 URL 只有 scheme 与 opaque 部分
         */
        @Test
        @DisplayName("不透明 URL 返回 opaque")
        void opaqueUrls() {
            ObjectNode mail = url("url", "mailto:someone@example.com");
            assertThat(mail.path("scheme").asText()).isEqualTo("mailto");
            assertThat(mail.path("opaque").asText()).isEqualTo("someone@example.com");
            assertThat(mail.has("host")).isFalse();

            assertThat(url("url", "tel:+8613800138000").path("opaque").asText())
                    .isEqualTo("+8613800138000");
            assertThat(url("url", "file:/etc/hosts").path("opaque").asText())
                    .isEqualTo("/etc/hosts");
        }

        /**
         * IPv6 主机带端口：host 去掉方括号，origin 保留方括号
         */
        @Test
        @DisplayName("IPv6 主机与端口")
        void ipv6Host() {
            ObjectNode r = url("url", "http://[2001:db8::1]:8080/p");

            assertThat(r.path("host").asText()).isEqualTo("2001:db8::1");
            assertThat(r.path("port").asInt()).isEqualTo(8080);
            assertThat(r.path("origin").asText()).isEqualTo("http://[2001:db8::1]:8080");
        }

        /**
         * IPv6 缺少右方括号时报错
         */
        @Test
        @DisplayName("IPv6 缺右方括号报错")
        void ipv6MissingBracket() {
            ObjectNode r = url("url", "http://[2001:db8::1/p");

            assertThat(r.path("error").asText())
                    .isEqualTo("IPv6 主机地址缺少右方括号: [2001:db8::1");
        }

        /**
         * 非数字端口报错
         */
        @Test
        @DisplayName("非法端口报错")
        void invalidPort() {
            ObjectNode r = url("url", "http://example.com:12a/x");

            assertThat(r.path("success").asBoolean()).isFalse();
            assertThat(r.path("error").asText()).isEqualTo("端口不合法: 12a");
        }

        /**
         * 查询串解码：%20 还原空格、'+' 不被当作空格、非法百分号编码原样保留
         */
        @Test
        @DisplayName("查询串解码边界")
        void queryDecoding() {
            ObjectNode r = url("url", "https://example.com/?q=a%20b&plus=x+y&bad=%zz&nov");

            assertThat(r.path("queryParams").path("q").asText()).isEqualTo("a b");
            assertThat(r.path("queryParams").path("plus").asText()).isEqualTo("x+y");
            assertThat(r.path("queryParams").path("bad").asText()).isEqualTo("%zz");
            assertThat(r.path("queryParams").path("nov").asText()).isEmpty();
        }

        /**
         * 空查询串返回空键值表
         */
        @Test
        @DisplayName("无查询串时 queryParams 为空")
        void emptyQuery() {
            ObjectNode r = url("url", "https://example.com/");

            assertThat(r.path("query").asText()).isEmpty();
            assertThat(r.path("queryParams").isObject()).isTrue();
            assertThat(r.path("queryParams").size()).isZero();
            assertThat(r.path("fragment").asText()).isEmpty();
        }

        /**
         * url 必填：缺省或纯空白都提示参数为空
         */
        @Test
        @DisplayName("url 必填")
        void urlRequired() {
            assertThat(url().path("error").asText()).isEqualTo("参数 url 不能为空");
            assertThat(url("url", "   ").path("error").asText()).isEqualTo("参数 url 不能为空");
        }
    }

    // ------------------------------------------------------------------
    // builtin_jwt_decode
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JWT 解码 builtin_jwt_decode")
    class JwtDecode {

        /** 构造 payload 时使用的当前时刻 */
        private final long now = Instant.now().getEpochSecond();

        /**
         * 标准三段 JWT：header/payload/signature 与时间声明全部输出
         */
        @Test
        @DisplayName("完整 JWT 输出 header、payload 与时间字段")
        void fullToken() {
            long exp = now + 3600;
            long iat = now - 60;
            String token = seg("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"cty\":\"JWT\",\"kid\":\"k1\"}")
                    + "." + seg("{\"sub\":\"u1\",\"role\":\"admin\",\"exp\":" + exp + ",\"iat\":" + iat
                    + ",\"nbf\":" + iat + "}")
                    + ".sig";

            ObjectNode r = jwt("token", token, "timezone", "UTC");

            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("algorithm").asText()).isEqualTo("HS256");
            assertThat(r.path("type").asText()).isEqualTo("JWT");
            assertThat(r.path("contentTyped").asText()).isEqualTo("JWT");
            assertThat(r.path("keyId").asText()).isEqualTo("k1");
            assertThat(r.path("header").path("alg").asText()).isEqualTo("HS256");
            assertThat(r.path("payload").path("sub").asText()).isEqualTo("u1");
            assertThat(r.path("signature").asText()).isEqualTo("sig");
            assertThat(r.path("headerSegments").asInt()).isEqualTo(3);
            assertThat(r.path("note").asText()).contains("未校验签名");
            assertThat(r.path("expiresAtEpoch").asLong()).isEqualTo(exp);
            assertThat(r.path("issuedAtEpoch").asLong()).isEqualTo(iat);
            assertThat(r.path("expiresAt").asText()).endsWith("Z");
            assertThat(r.path("notBefore").asText()).endsWith("Z");
            assertThat(r.path("expired").asBoolean()).isFalse();
            assertThat(r.path("secondsToExpiry").asLong()).isPositive();
            assertThat(r.path("notYetValid").asBoolean()).isFalse();
        }

        /**
         * 过期与尚未生效的时间声明分别给出 expired / notYetValid
         */
        @Test
        @DisplayName("exp 已过期、nbf 未生效")
        void expiredAndNotYetValid() {
            String token = seg("{\"alg\":\"none\"}") + "."
                    + seg("{\"exp\":" + (now - 100) + ",\"nbf\":" + (now + 100) + "}") + ".sig";

            ObjectNode r = jwt("token", token, "timezone", "UTC");

            assertThat(r.path("expired").asBoolean()).isTrue();
            assertThat(r.path("secondsToExpiry").asLong()).isNegative();
            assertThat(r.path("notYetValid").asBoolean()).isTrue();
        }

        /**
         * 时间声明兼容字符串化时间戳；无法解析的声明被忽略且不报错
         */
        @Test
        @DisplayName("字符串化与非法时间声明")
        void stringifiedClaims() {
            ObjectNode r = jwt("token", seg("{\"alg\":\"HS256\"}") + "."
                    + seg("{\"exp\":\"1767225600\"}") + ".sig", "timezone", "UTC");
            assertThat(r.path("expiresAtEpoch").asLong()).isEqualTo(1767225600L);
            assertThat(r.path("expiresAt").asText()).isEqualTo("2026-01-01T00:00:00Z");

            ObjectNode bad = jwt("token", seg("{\"alg\":\"HS256\"}") + "."
                    + seg("{\"exp\":\"tomorrow\",\"iat\":null}") + ".sig");
            assertThat(bad.path("success").asBoolean()).isTrue();
            assertThat(bad.has("expiresAt")).isFalse();
            assertThat(bad.has("expired")).isFalse();
            assertThat(bad.has("issuedAt")).isFalse();
        }

        /**
         * 时区影响时间声明的格式化输出
         */
        @Test
        @DisplayName("时区生效：Asia/Shanghai 输出 +08:00")
        void timezoneFormatting() {
            ObjectNode r = jwt("token", seg("{\"alg\":\"HS256\"}") + "."
                    + seg("{\"exp\":1767225600}") + ".sig", "timezone", "Asia/Shanghai");

            assertThat(r.path("expiresAt").asText()).isEqualTo("2026-01-01T08:00:00+08:00");
        }

        /**
         * part 参数只保留关心的内容：header / payload 二选一
         */
        @Test
        @DisplayName("part 只返回指定段")
        void partSelection() {
            String token = seg("{\"alg\":\"HS256\"}") + "." + seg("{\"sub\":\"u1\"}") + ".sig";

            ObjectNode header = jwt("token", token, "part", "header");
            assertThat(header.has("header")).isTrue();
            assertThat(header.has("payload")).isFalse();

            ObjectNode payload = jwt("token", token, "part", "payload");
            assertThat(payload.has("header")).isFalse();
            assertThat(payload.has("payload")).isTrue();

            ObjectNode all = jwt("token", token);
            assertThat(all.has("header")).isTrue();
            assertThat(all.has("payload")).isTrue();
        }

        /**
         * 容忍 Bearer 前缀；两段式（无签名）JWT 也可解码
         */
        @Test
        @DisplayName("Bearer 前缀与两段式 JWT")
        void bearerPrefixAndTwoSegments() {
            String token = "Bearer " + seg("{\"alg\":\"HS256\"}") + "." + seg("{\"sub\":\"u1\"}");

            ObjectNode r = jwt("token", token, "timezone", "UTC");

            assertThat(r.path("success").asBoolean()).isTrue();
            assertThat(r.path("headerSegments").asInt()).isEqualTo(2);
            assertThat(r.has("signature")).isFalse();
        }

        /**
         * token 必填
         */
        @Test
        @DisplayName("token 必填")
        void tokenRequired() {
            assertThat(jwt().path("error").asText()).isEqualTo("参数 token 不能为空");
            assertThat(jwt("token", "  ").path("error").asText()).isEqualTo("参数 token 不能为空");
        }

        /**
         * 段数不是 2/3 时报错并给出实际段数
         */
        @Test
        @DisplayName("段数非法")
        void invalidSegmentCount() {
            assertThat(jwt("token", "abc").path("error").asText())
                    .isEqualTo("不是合法的 JWT：期望 header.payload.signature 三段，实际 1 段");
            assertThat(jwt("token", "a.b.c.d").path("error").asText())
                    .isEqualTo("不是合法的 JWT：期望 header.payload.signature 三段，实际 4 段");
        }

        /**
         * 段为空 / 非 Base64URL / 解码后非 JSON 对象的错误提示
         */
        @Test
        @DisplayName("段内容非法")
        void invalidSegments() {
            assertThat(jwt("token", ".e30.sig").path("error").asText())
                    .isEqualTo("header 段为空，无法解码");
            assertThat(jwt("token", "e30..sig").path("error").asText())
                    .isEqualTo("payload 段为空，无法解码");
            assertThat(jwt("token", "!!!.e30.sig").path("error").asText())
                    .isEqualTo("header 段不是合法的 Base64URL 编码");
            assertThat(jwt("token", seg("hello") + ".e30.sig").path("error").asText())
                    .isEqualTo("header 段解码后不是合法 JSON，请确认传入的是标准 JWT");
            assertThat(jwt("token", seg("123") + ".e30.sig").path("error").asText())
                    .isEqualTo("header 段解码后不是 JSON 对象");
        }

        /**
         * 非法时区在解码前优先报错
         */
        @Test
        @DisplayName("非法时区报错")
        void invalidTimezone() {
            ObjectNode r = jwt("token", seg("{\"alg\":\"HS256\"}") + "." + seg("{}") + ".sig",
                    "timezone", "Mars/Olympus");

            assertThat(r.path("error").asText())
                    .isEqualTo("无效的时区: Mars/Olympus（示例 Asia/Shanghai、UTC）");
        }
    }
}
