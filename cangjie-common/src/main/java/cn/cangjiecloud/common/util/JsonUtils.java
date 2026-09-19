package cn.cangjiecloud.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Jackson 的统一 JSON 工具。
 * <p>
 * 替换原 fastjson 的使用：{@code parseObject} 返回 {@link ObjectNode}（可写的 Map 风格节点），
 * {@code parseArray} 返回 {@link ArrayNode}；需要纯 Map/List 时使用 {@code parseMap}、{@code parseList}。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    // ---------------- 序列化 ----------------

    public static String toJSONString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * 将对象转换为指定类型（用于跨对象结构拷贝，替代 fastjson 的
     * {@code JSON.parseObject(JSON.toJSONString(obj), type)}）。
     */
    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    public static <T> T convert(Object value, TypeReference<T> type) {
        return MAPPER.convertValue(value, type);
    }

    // ---------------- 反序列化 ----------------

    public static <T> T parseObject(String text, Class<T> type) {
        try {
            return MAPPER.readValue(text, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static <T> T parseObject(String text, TypeReference<T> type) {
        try {
            return MAPPER.readValue(text, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static ObjectNode parseObject(String text) {
        try {
            return (ObjectNode) MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    public static <T> List<T> parseList(String text, Class<T> type) {
        try {
            return MAPPER.readValue(text, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    public static ArrayNode parseArray(String text) {
        try {
            return (ArrayNode) MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    /** 解析为 Map 列表，元素保留嵌套 Map/List 结构。 */
    public static List<Map<String, Object>> parseListOfMap(String text) {
        return parseList(text, Map.class).stream()
                .map(m -> (Map<String, Object>) m)
                .toList();
    }

    /** 解析为通用 Map，返回保持插入顺序的 LinkedHashMap。 */
    public static Map<String, Object> parseMap(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    /** 将 {@link JsonNode} 转换为指定类型（替代 fastjson 的 {@code toJavaObject}）。 */
    public static <T> T toObject(JsonNode node, Class<T> type) {
        return MAPPER.convertValue(node, type);
    }

    public static ObjectNode newObject() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode newArray() {
        return MAPPER.createArrayNode();
    }

    /** ArrayNode → 元素为 ObjectNode 的列表（替代 fastjson 的 {@code toJavaList(JSONObject.class)}）。 */
    public static List<ObjectNode> toObjectList(ArrayNode array) {
        List<ObjectNode> list = new ArrayList<>(array.size());
        array.forEach(node -> list.add((ObjectNode) node));
        return list;
    }
}
