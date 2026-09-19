package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 LLM 调用的业务上下文。
 * <p>
 * 随 {@link ChatRequest} 透传到模型客户端，最终以 langchain4j
 * {@code ChatRequestOptions.listenerAttributes()} 的形式交给 {@code ChatModelListener}。
 * <p>
 * 之所以显式透传而不是依赖 ThreadLocal（如 TraceContext）：流式调用在模型线程池上回调，
 * 线程上下文不延续，只有随请求携带才能保证 listener 拿得到 traceId / 会话 / 应用归属。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTraceContext {

    /** listener attributes 的键名前缀：避免与 langchain4j 自身或其它监听器冲突 */
    public static final String KEY_TRACE_ID = "cangjie.traceId";
    public static final String KEY_REQUEST_ID = "cangjie.requestId";
    public static final String KEY_APP_ID = "cangjie.appId";
    public static final String KEY_APP_NAME = "cangjie.appName";
    public static final String KEY_SESSION_ID = "cangjie.sessionId";
    public static final String KEY_USER_ID = "cangjie.userId";
    public static final String KEY_MODEL_ID = "cangjie.modelId";
    public static final String KEY_MODEL_NAME = "cangjie.modelName";

    /** 链路 ID */
    private String traceId;

    /**
     * 业务请求 ID（如 chatcmpl-xxx、session-summary-xxx）。
     * 置空时由监听器回填厂商返回的真实 response id。
     */
    private String requestId;

    /** 应用 ID */
    private String appId;

    /** 应用名称 */
    private String appName;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 模型 ID */
    private String modelId;

    /** 模型显示名称 */
    private String modelName;

    /**
     * 转为 langchain4j listener attributes。
     */
    public Map<Object, Object> toListenerAttributes() {
        Map<Object, Object> attributes = new LinkedHashMap<>();
        put(attributes, KEY_TRACE_ID, traceId);
        put(attributes, KEY_REQUEST_ID, requestId);
        put(attributes, KEY_APP_ID, appId);
        put(attributes, KEY_APP_NAME, appName);
        put(attributes, KEY_SESSION_ID, sessionId);
        put(attributes, KEY_USER_ID, userId);
        put(attributes, KEY_MODEL_ID, modelId);
        put(attributes, KEY_MODEL_NAME, modelName);
        return attributes;
    }

    /**
     * 从 listener attributes 还原业务上下文；没有任何业务字段时返回 null。
     */
    public static ChatTraceContext fromListenerAttributes(Map<Object, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        ChatTraceContext context = ChatTraceContext.builder()
                .traceId(asString(attributes.get(KEY_TRACE_ID)))
                .requestId(asString(attributes.get(KEY_REQUEST_ID)))
                .appId(asString(attributes.get(KEY_APP_ID)))
                .appName(asString(attributes.get(KEY_APP_NAME)))
                .sessionId(asString(attributes.get(KEY_SESSION_ID)))
                .userId(asString(attributes.get(KEY_USER_ID)))
                .modelId(asString(attributes.get(KEY_MODEL_ID)))
                .modelName(asString(attributes.get(KEY_MODEL_NAME)))
                .build();
        return context.isEmpty() ? null : context;
    }

    private boolean isEmpty() {
        return traceId == null && requestId == null && appId == null && appName == null
                && sessionId == null && userId == null && modelId == null && modelName == null;
    }

    private static void put(Map<Object, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
