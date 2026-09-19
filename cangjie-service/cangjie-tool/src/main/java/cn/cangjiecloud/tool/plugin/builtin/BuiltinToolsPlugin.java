package cn.cangjiecloud.tool.plugin.builtin;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.plugin.PluginContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内置本地工具集合插件（纯 JDK 实现，无外网依赖）。
 * <p>
 * 一个插件承载多个内置工具，按 metadata.toolId 分发：
 * <ul>
 *     <li>builtin_hash    —— MD5 / SHA-1 / SHA-256 哈希</li>
 *     <li>builtin_uuid    —— 生成 UUID</li>
 *     <li>builtin_base64  —— Base64 编码/解码</li>
 *     <li>builtin_urlcode —— URL 编码/解码</li>
 *     <li>builtin_json_extract —— JSONPath 字段提取</li>
 * </ul>
 * 对应 tool 表记录的 implementation 配置为本类全限定名，toolType=PLUGIN。
 */
@Component
public class BuiltinToolsPlugin extends AbstractBuiltinPlugin {

    @Override
    public String getName() {
        return "builtin_local_tools";
    }

    @Override
    public String getDescription() {
        return "内置本地工具集合：哈希、UUID、Base64、URL 编解码";
    }

    @Override
    public String getType() {
        return "tool";
    }

    @Override
    public Object execute(PluginContext context) {
        Map<String, Object> params = paramsOf(context);
        return switch (toolIdOf(context)) {
            case "builtin_hash" -> hash(params);
            case "builtin_uuid" -> generateUuid(params);
            case "builtin_base64" -> base64(params);
            case "builtin_urlcode" -> urlCode(params);
            case "builtin_json_extract" -> jsonExtract(params);
            default -> error("未知的内置工具: " + toolIdOf(context));
        };
    }

    /** 文本哈希：algorithm 支持 md5 / sha1 / sha256（默认 md5） */
    private ObjectNode hash(Map<String, Object> params) {
        String text = raw(params.get("text"));
        String algorithm = str(params.getOrDefault("algorithm", "md5")).toLowerCase();
        if (text.isEmpty()) {
            return error("text 不能为空");
        }
        String alg = switch (algorithm) {
            case "md5" -> "MD5";
            case "sha1", "sha-1" -> "SHA-1";
            case "sha256", "sha-256" -> "SHA-256";
            default -> "";
        };
        if (alg.isEmpty()) {
            return error("不支持的算法: " + algorithm + "（支持 md5 / sha1 / sha256）");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return ok(new LinkedHashMap<>(Map.of(
                    "algorithm", alg.replace("-", "").toLowerCase(),
                    "text", text,
                    "hash", sb.toString())));
        } catch (Exception e) {
            return error("哈希计算失败: " + e.getMessage());
        }
    }

    /** 生成 UUID：count 1~100，默认 1；uppercase 控制大小写；hyphen=false 去掉连字符 */
    private ObjectNode generateUuid(Map<String, Object> params) {
        int count = clamp(intVal(params.get("count"), 1), 1, 100);
        boolean uppercase = boolVal(params.get("uppercase"), false);
        boolean hyphen = boolVal(params.get("hyphen"), true);
        List<String> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String u = UUID.randomUUID().toString();
            if (!hyphen) u = u.replace("-", "");
            if (uppercase) u = u.toUpperCase();
            uuids.add(u);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", uuids.size());
        data.put("uuid", uuids.get(0));
        data.put("uuids", uuids);
        return ok(data);
    }

    /** Base64 编码/解码：mode=encode（默认）/ decode */
    private ObjectNode base64(Map<String, Object> params) {
        String text = raw(params.get("text"));
        String mode = str(params.getOrDefault("mode", "encode")).toLowerCase();
        if (text.isEmpty()) {
            return error("text 不能为空");
        }
        try {
            String result;
            if ("decode".equals(mode)) {
                byte[] decoded = Base64.getDecoder().decode(text.trim());
                result = new String(decoded, StandardCharsets.UTF_8);
            } else if ("encode".equals(mode)) {
                result = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            } else {
                return error("mode 只能是 encode 或 decode");
            }
            return ok(new LinkedHashMap<>(Map.of("mode", mode, "text", text, "result", result)));
        } catch (IllegalArgumentException e) {
            return error("Base64 解码失败：输入不是合法的 Base64 内容");
        }
    }

    /** URL 编码/解码：mode=encode（默认）/ decode */
    private ObjectNode urlCode(Map<String, Object> params) {
        String text = raw(params.get("text"));
        String mode = str(params.getOrDefault("mode", "encode")).toLowerCase();
        if (text.isEmpty()) {
            return error("text 不能为空");
        }
        try {
            String result;
            if ("decode".equals(mode)) {
                result = URLDecoder.decode(text, StandardCharsets.UTF_8);
            } else if ("encode".equals(mode)) {
                result = URLEncoder.encode(text, StandardCharsets.UTF_8);
            } else {
                return error("mode 只能是 encode 或 decode");
            }
            return ok(new LinkedHashMap<>(Map.of("mode", mode, "text", text, "result", result)));
        } catch (Exception e) {
            return error("URL " + mode + " 失败: " + e.getMessage());
        }
    }

    /**
     * JSON 字段提取：path 支持点路径（data.items.0.name）或标准 JSONPath（$.data.items[0].name）。
     */
    private ObjectNode jsonExtract(Map<String, Object> params) {
        String jsonText = raw(params.get("json"));
        String path = str(params.get("path"));
        if (jsonText.isEmpty()) {
            return error("json 不能为空");
        }
        if (path.isEmpty()) {
            return error("path 不能为空");
        }
        try {
            JsonNode root = JsonUtils.mapper().readTree(jsonText);
            JsonNode value = resolvePath(root, path);
            boolean found = value != null && !value.isNull();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path);
            data.put("found", found);
            data.put("value", found ? value : null);
            return ok(data);
        } catch (Exception e) {
            return error("JSON 解析或提取失败: " + e.getMessage());
        }
    }

    /**
     * 按路径取字段：点路径的每一段与 JSONPath 的 {@code .field} / {@code [index]} 等价，
     * 数组下标既可用 {@code [0]} 也可用点路径的 {@code .0}。路径不存在时返回 null。
     */
    private static JsonNode resolvePath(JsonNode root, String path) {
        String expression = path.startsWith("$") ? path.substring(1) : "." + path;
        JsonNode current = root;
        int index = 0;
        while (current != null && index < expression.length()) {
            char c = expression.charAt(index);
            if (c == '.') {
                index++;
                int end = index;
                while (end < expression.length() && expression.charAt(end) != '.' && expression.charAt(end) != '[') {
                    end++;
                }
                String field = expression.substring(index, end);
                current = current.isArray() && field.matches("\\d+")
                        ? current.get(Integer.parseInt(field))
                        : current.get(field);
                index = end;
            } else if (c == '[') {
                int close = expression.indexOf(']', index);
                if (close < 0) {
                    return null;
                }
                String token = expression.substring(index + 1, close).trim();
                if (token.length() >= 2 && (token.startsWith("'") || token.startsWith("\""))) {
                    current = current.get(token.substring(1, token.length() - 1));
                } else if (token.matches("\\d+")) {
                    current = current.get(Integer.parseInt(token));
                } else {
                    return null;
                }
                index = close + 1;
            } else {
                return null;
            }
        }
        return current;
    }
}
