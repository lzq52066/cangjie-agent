package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置解析类工具插件（纯 JDK 实现，无外网依赖）。
 * <p>
 * 按 metadata.toolId 分发：
 * <ul>
 *     <li>builtin_url_parse  —— 拆解 URL 的协议/主机/端口/路径/查询参数/锚点</li>
 *     <li>builtin_jwt_decode —— 解码 JWT 的 header 与 payload（不校验签名）</li>
 * </ul>
 * 对应 tool 表记录的 implementation 配置为本类全限定名，toolType=PLUGIN。
 */
@Component
public class ParserToolsPlugin extends AbstractBuiltinPlugin {

    /** scheme 头：字母开头，后接字母数字与 +-. ；"//" 决定是否为带权威段的 URL */
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):(//)?");
    private static final Pattern PORT = Pattern.compile("\\d{1,5}");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public String getName() {
        return "builtin_parser_tools";
    }

    @Override
    public String getDescription() {
        return "内置解析工具集合：URL 拆解、JWT 解码";
    }

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = paramsOf(context);
        return switch (toolIdOf(context)) {
            case "builtin_url_parse" -> urlParse(params);
            case "builtin_jwt_decode" -> jwtDecode(params);
            default -> error("未知的内置工具: " + toolIdOf(context));
        };
    }

    // ------------------------------------------------------------------ URL

    /**
     * 拆解 URL。容忍大模型常见的写法问题：缺少协议前缀（www.example.com/a）、
     * 带尖括号或引号、结尾多余空格等。
     */
    private ObjectNode urlParse(Map<String, Object> params) {
        String input = stripWrappers(raw(params.get("url")));
        if (input.isEmpty()) {
            return missing("url");
        }
        // 无协议且不是 mailto:/tel: 这类不透明形式时，按 https 补齐
        if (!SCHEME.matcher(input).find()) {
            input = "https://" + input;
        }

        Matcher matcher = SCHEME.matcher(input);
        if (!matcher.find()) {
            return error("无法解析的 URL: " + input);
        }
        String scheme = matcher.group(1).toLowerCase();
        boolean hierarchical = matcher.group(2) != null;
        String rest = input.substring(matcher.end());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", input);
        data.put("scheme", scheme);

        if (!hierarchical) {
            // mailto: / tel: / data: 等不透明 URL，只有 scheme 与剩余部分
            data.put("opaque", rest);
            return ok(data);
        }

        String fragment = "";
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash + 1);
            rest = rest.substring(0, hash);
        }
        String queryString = "";
        int question = rest.indexOf('?');
        if (question >= 0) {
            queryString = rest.substring(question + 1);
            rest = rest.substring(0, question);
        }

        String authority = rest;
        String path = "";
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            authority = rest.substring(0, slash);
            path = rest.substring(slash);
        }

        String userInfo = "";
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at);
            authority = authority.substring(at + 1);
        }

        String host = authority.toLowerCase();
        String port = "";
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            if (close < 0) {
                return error("IPv6 主机地址缺少右方括号: " + authority);
            }
            int colon = host.indexOf(':', close);
            if (colon >= 0) {
                port = host.substring(colon + 1);
            }
            host = host.substring(1, close);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                port = host.substring(colon + 1);
                host = host.substring(0, colon);
            }
        }
        if (!port.isEmpty() && !PORT.matcher(port).matches()) {
            return error("端口不合法: " + port);
        }
        int portValue = port.isEmpty() ? defaultPort(scheme) : Integer.parseInt(port);

        data.put("host", host);
        data.put("port", portValue);
        data.put("explicitPort", port.isEmpty() ? null : portValue);
        data.put("path", path);
        data.put("query", queryString);
        data.put("queryParams", parseQuery(queryString));
        data.put("fragment", fragment);
        data.put("origin", scheme + "://" + authorityHost(host, port));
        if (!userInfo.isEmpty()) {
            // 不把口令原文回传给模型，避免凭据进入对话上下文
            int colon = userInfo.indexOf(':');
            data.put("username", colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            data.put("hasPassword", colon >= 0 && !userInfo.substring(colon + 1).isEmpty());
        }
        return ok(data);
    }

    /** 查询串转有序键值表；同名键出现多次时合并为数组 */
    private Map<String, Object> parseQuery(String queryString) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return result;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            name = decode(name);
            value = decode(value);
            Object existing = result.get(name);
            if (existing == null && !result.containsKey(name)) {
                result.put(name, value);
            } else if (existing instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Object> values = (List<Object>) list;
                values.add(value);
            } else {
                List<Object> values = new ArrayList<>();
                values.add(existing);
                values.add(value);
                result.put(name, values);
            }
        }
        return result;
    }

    private static String decode(String text) {
        try {
            // '+' 在路径/查询中不必然代表空格，先转义为 %2B 之外的形式再解码
            return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    private static String authorityHost(String host, String port) {
        boolean needsBrackets = host.contains(":");
        String authority = needsBrackets ? "[" + host + "]" : host;
        return port.isEmpty() ? authority : authority + ":" + port;
    }

    private static int defaultPort(String scheme) {
        return switch (scheme) {
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            case "ftp" -> 21;
            default -> -1;
        };
    }

    // ----------------------------------------------------------------- JWT

    /**
     * 解码 JWT 的 header 与 payload。
     * <p>
     * 只做 Base64URL 解码，<b>不校验签名</b>，因此解码出的内容不可作为授权依据，
     * 仅用于向用户解释令牌里写了什么。
     */
    private ObjectNode jwtDecode(Map<String, Object> params) {
        String token = stripWrappers(raw(params.get("token")));
        if (token.isEmpty()) {
            return missing("token");
        }
        if (token.regionMatches(true, 0, "bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            return error("不是合法的 JWT：期望 header.payload.signature 三段，实际 " + parts.length + " 段");
        }
        String wanted = str(params.getOrDefault("part", "all")).toLowerCase();

        ZoneId zone;
        String timezone = str(params.get("timezone"));
        try {
            zone = timezone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return error("无效的时区: " + timezone + "（示例 Asia/Shanghai、UTC）");
        }

        ObjectNode header;
        ObjectNode payload;
        try {
            header = decodeSegment(parts[0], "header");
            payload = decodeSegment(parts[1], "payload");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithm", header.get("alg"));
        data.put("type", header.get("typ"));
        data.put("contentTyped", header.get("cty"));
        data.put("keyId", header.get("kid"));
        if ("all".equals(wanted) || "header".equals(wanted)) {
            data.put("header", header);
        }
        if ("all".equals(wanted) || "payload".equals(wanted)) {
            data.put("payload", payload);
        }
        if (parts.length == 3) {
            data.put("signature", parts[2]);
        }
        data.put("headerSegments", parts.length);
        data.put("note", "仅解码 Base64URL 内容，未校验签名；不可据此判断令牌是否可信");

        putTime(data, payload, "exp", "expiresAt", zone);
        putTime(data, payload, "iat", "issuedAt", zone);
        putTime(data, payload, "nbf", "notBefore", zone);
        Instant now = Instant.now();
        Long exp = longClaim(payload, "exp");
        if (exp != null) {
            data.put("expired", now.getEpochSecond() >= exp);
            data.put("secondsToExpiry", exp - now.getEpochSecond());
        }
        Long nbf = longClaim(payload, "nbf");
        if (nbf != null) {
            data.put("notYetValid", now.getEpochSecond() < nbf);
        }
        return ok(data);
    }

    /** Base64URL 段解码为 JSON 对象 */
    private ObjectNode decodeSegment(String segment, String name) {
        String cleaned = segment;
        int eq = cleaned.indexOf('=');
        if (eq >= 0) {
            cleaned = cleaned.substring(0, eq);
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(name + " 段为空，无法解码");
        }
        int mod = cleaned.length() % 4;
        if (mod > 0) {
            cleaned = cleaned + "=".repeat(4 - mod);
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " 段不是合法的 Base64URL 编码");
        }
        JsonNode parsed;
        try {
            parsed = JsonUtils.mapper().readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException(name + " 段解码后不是合法 JSON，请确认传入的是标准 JWT");
        }
        if (!(parsed instanceof ObjectNode object)) {
            throw new IllegalArgumentException(name + " 段解码后不是 JSON 对象");
        }
        return object;
    }

    private void putTime(Map<String, Object> data, ObjectNode payload, String claim, String field, ZoneId zone) {
        Long epochSecond = longClaim(payload, claim);
        if (epochSecond == null) {
            return;
        }
        data.put(field, ISO.format(Instant.ofEpochSecond(epochSecond).atZone(zone)));
        data.put(field + "Epoch", epochSecond);
    }

    /** 读取时间类声明：兼容数字与字符串化的秒级时间戳 */
    private static Long longClaim(ObjectNode payload, String claim) {
        JsonNode value = payload.get(claim);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- 公共小工具

    /** 去掉模型常加的包裹符号：<url>、"url"、'url'、``` 之外的空格 */
    private static String stripWrappers(String text) {
        String value = text.trim();
        while (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            boolean wrapped = (first == '<' && last == '>') || (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (!wrapped) {
                break;
            }
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
