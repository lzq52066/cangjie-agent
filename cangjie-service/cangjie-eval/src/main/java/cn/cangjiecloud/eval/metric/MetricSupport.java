package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标实现的公共支撑：判定输出的取数、正文裁剪、余弦相似度。
 * <p>
 * 单独抽出来是因为四个指标都要做同一件麻烦事 —— 从模型输出里把 JSON 抠出来。各家
 * 兼容模型会自由发挥：包一层 ```json 围栏、在前面写一句「好的，结果如下：」、
 * 把数组写成对象。这些都得容住，否则指标看似「均分很低」，实际是解析器在闹脾气。
 */
public final class MetricSupport {

    /** 单条正文入 prompt 的裁剪长度：判定用不着整篇文档，超长只会把 token 烧在噪声上 */
    private static final int MAX_PROMPT_CHARS = 2000;

    private MetricSupport() {
    }

    /**
     * 从模型自由输出里提取第一个完整的 JSON 对象或数组；提取不出返回 null（调用方按未评测处理）。
     */
    public static JsonNode extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String raw = stripFence(text);
        int objStart = raw.indexOf('{');
        int arrStart = raw.indexOf('[');
        int start = firstCandidate(objStart, arrStart);
        if (start < 0) {
            return null;
        }
        char open = raw.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = matchingClose(raw, start, open, close);
        if (end <= start) {
            return null;
        }
        try {
            return JsonUtils.mapper().readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static int firstCandidate(int objStart, int arrStart) {
        if (objStart < 0) {
            return arrStart;
        }
        if (arrStart < 0) {
            return objStart;
        }
        return Math.min(objStart, arrStart);
    }

    /**
     * 括号配对扫描：跳过字符串字面量里的括号，避免把 reason 文本中的 "}" 当成结束。
     */
    private static int matchingClose(String raw, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int contentStart = trimmed.indexOf('\n');
        int fenceEnd = trimmed.lastIndexOf("```");
        if (contentStart > 0 && fenceEnd > contentStart) {
            return trimmed.substring(contentStart + 1, fenceEnd).trim();
        }
        return trimmed;
    }

    /**
     * 解析标注里的 ID 列表（JSON 数组文本）。
     * <p>
     * 除纯字符串外也兼容对象写法（取 documentId / id / paragraphId 字段）：早期数据里有对象形态，
     * 拿 {@code toString()} 整段 JSON 去比对 ID 永远对不上，只会得到一个伪 0 分。
     * 解不出来返回空列表（调用方按「没标注」处理，不抛异常打断整轮评估）。
     */
    public static List<String> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode array;
        try {
            array = MetricSupport.extractJson(json);
        } catch (Exception e) {
            return List.of();
        }
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode node : array) {
            String id = null;
            if (node.isObject()) {
                for (String field : new String[]{"documentId", "paragraphId", "id"}) {
                    JsonNode value = node.get(field);
                    if (value != null && value.isTextual() && !value.asText().isBlank()) {
                        id = value.asText().trim();
                        break;
                    }
                }
            } else if (node.isValueNode() && !node.isNull()) {
                id = node.asText().trim();
            }
            if (id != null && !id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 取数组字段：字段本身是数组直接返回；传进来的节点是数组则原样返回；否则 null。
     */
    public static JsonNode arrayNode(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        JsonNode value = node.get(field);
        return value != null && value.isArray() ? value : null;
    }

    /**
     * 字符串数组 → Java 列表，过滤空白项与非文本元素（模型偶尔混进数字或对象）。
     */
    public static List<String> toTextList(JsonNode array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (JsonNode node : array) {
            if (node.isTextual() && !node.asText().isBlank()) {
                list.add(node.asText().trim());
            } else if (node.isObject()) {
                // 对象形态：常见是 {"text": "..."} 或 {"claim": "..."}，取第一个文本字段
                String text = firstTextValue(node);
                if (text != null) {
                    list.add(text);
                }
            }
        }
        return list;
    }

    private static String firstTextValue(JsonNode node) {
        for (JsonNode child : node) {
            if (child.isTextual() && !child.asText().isBlank()) {
                return child.asText().trim();
            }
        }
        return null;
    }

    /**
     * 判定值：兼容 true/false、1/0、"yes"/"no"、"supported"/"refuted" 等多种写法。
     *
     * @return null 表示这个字段压根没给出可辨的判定（调用方按「未判定」处理）
     */
    public static Boolean toVerdict(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.isBoolean() ? node : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            double d = value.asDouble();
            // 只认二值编码；0.5 这类中间值说明模型没按口径来，宁可不判也别猜
            if (d >= 0.999) {
                return Boolean.TRUE;
            }
            if (d <= 0.001) {
                return Boolean.FALSE;
            }
            return null;
        }
        String text = value.asText().trim().toLowerCase();
        return switch (text) {
            case "true", "yes", "y", "1", "supported", "faithful", "relevant", "correct" -> Boolean.TRUE;
            case "false", "no", "n", "0", "unsupported", "refuted", "irrelevant", "incorrect" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * 片段编号成 prompt 里的引用块：判定要能按序号回指，否则「第 3 条相关」这种输出对不上原文。
     */
    public static String numbered(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append('[').append(i + 1).append("] ")
                    .append(truncate(items.get(i))).append('\n');
        }
        return sb.toString();
    }

    public static String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= MAX_PROMPT_CHARS ? content : content.substring(0, MAX_PROMPT_CHARS) + "...";
    }

    /**
     * 余弦相似度，夹在 [0,1] 内。
     * <p>
     * embedding 空间里负的余弦代表「语义相反」，但指标口径是「一致度」，负值取 0 而不是
     * 线性映射到 [0,1] —— 后者会把「完全无关」抬成一个看起来还行的分数。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        double similarity = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(0.0, Math.min(1.0, similarity));
    }
}
