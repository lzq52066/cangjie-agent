package cn.cangjiecloud.observability.context;

/**
 * 可观测性链路上下文（基于 ThreadLocal，用于 traceId 跨方法传递）
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    public static void setSessionId(String sessionId) {
        SESSION_ID_HOLDER.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_ID_HOLDER.get();
    }

    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        SESSION_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
    }
}