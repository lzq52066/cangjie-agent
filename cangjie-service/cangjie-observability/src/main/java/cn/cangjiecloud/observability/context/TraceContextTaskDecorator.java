package cn.cangjiecloud.observability.context;

import org.springframework.core.task.TaskDecorator;

/**
 * 链路上下文任务装饰器：将提交线程的 TraceContext 传递到线程池执行线程，
 * 修复 @Async / 线程池场景下链路追踪断裂问题。
 * <p>
 * 线程池线程会被复用，执行结束后恢复原上下文，避免污染后续任务。
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String traceId = TraceContext.getTraceId();
        String sessionId = TraceContext.getSessionId();
        String userId = TraceContext.getUserId();
        return () -> {
            String oldTrace = TraceContext.getTraceId();
            String oldSession = TraceContext.getSessionId();
            String oldUser = TraceContext.getUserId();
            try {
                if (traceId != null) {
                    TraceContext.setTraceId(traceId);
                }
                if (sessionId != null) {
                    TraceContext.setSessionId(sessionId);
                }
                if (userId != null) {
                    TraceContext.setUserId(userId);
                }
                runnable.run();
            } finally {
                TraceContext.setTraceId(oldTrace);
                TraceContext.setSessionId(oldSession);
                TraceContext.setUserId(oldUser);
            }
        };
    }
}
