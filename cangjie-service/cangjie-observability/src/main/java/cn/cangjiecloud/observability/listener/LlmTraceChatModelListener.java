package cn.cangjiecloud.observability.listener;

import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.core.model.LlmErrorMapper;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 调用统一追踪监听器。
 * <p>
 * 挂在 langchain4j 的 {@code ChatModelListener} 上，把每一次模型调用（同步与流式、主链路与后台任务）
 * 收敛为一条 llm_trace：请求消息、响应正文、真实 token usage、耗时、异常分类。
 * <p>
 * 业务归属（traceId / 应用 / 会话 / 用户）由 {@link ChatTraceContext} 随请求透传，
 * 因此不依赖 ThreadLocal，流式调用在模型线程池上回调也能正确归集。
 * <p>
 * 监听器抛出的异常会被 langchain4j 吞掉并告警，这里主动兜住，确保可观测性失败绝不影响对话主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTraceChatModelListener implements ChatModelListener {

    /** attributes 中记录调用开始时间的键：onRequest 写入，onResponse/onError 读出算耗时 */
    private static final String KEY_START_MS = "cangjie.listener.startMs";

    private final ILlmTraceRecorder llmTraceRecorder;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(KEY_START_MS, System.currentTimeMillis());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        try {
            ChatResponse response = responseContext.chatResponse();
            TokenUsage usage = response.tokenUsage();

            ILlmTraceRecorder.LlmTraceRecord.LlmTraceRecordBuilder builder =
                    baseRecord(responseContext.attributes(), responseContext.chatRequest())
                            .requestId(resolveRequestId(responseContext.attributes(), response))
                            .modelName(resolveModelName(responseContext.attributes(), response))
                            .responseContent(response.aiMessage() == null ? null : response.aiMessage().text())
                            .finishReason(response.finishReason() == null ? null : response.finishReason().name());
            if (usage != null) {
                // 厂商不返 usage 的分项时 langchain4j 给的是 null：直接拆箱会 NPE，被外层 catch 吞掉后
                // 整条成功调用的 trace 一起丢了，代价远大于少记一组 token
                builder.inputTokens(toLong(usage.inputTokenCount()))
                        .outputTokens(toLong(usage.outputTokenCount()))
                        .totalTokens(toLong(usage.totalTokenCount()));
            }
            llmTraceRecorder.recordSuccess(builder.build());
        } catch (Exception e) {
            log.warn("记录 LLM 调用成功 trace 失败: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        try {
            Throwable error = errorContext.error();
            ILlmTraceRecorder.LlmTraceRecord record =
                    baseRecord(errorContext.attributes(), errorContext.chatRequest())
                            .requestId(resolveRequestId(errorContext.attributes(), null))
                            .modelName(resolveModelName(errorContext.attributes(), null))
                            .build();
            llmTraceRecorder.recordFailure(record, LlmErrorMapper.map(error));
        } catch (Exception e) {
            log.warn("记录 LLM 调用失败 trace 失败: {}", e.getMessage());
        }
    }

    private ILlmTraceRecorder.LlmTraceRecord.LlmTraceRecordBuilder baseRecord(
            Map<Object, Object> attributes, ChatRequest lcRequest) {
        long startMs = resolveStartMs(attributes);
        ILlmTraceRecorder.LlmTraceRecord.LlmTraceRecordBuilder builder =
                ILlmTraceRecorder.LlmTraceRecord.builder()
                        .duration(System.currentTimeMillis() - startMs)
                        .startTimeMs(startMs)
                        .firstTokenMs(resolveFirstTokenMs(attributes))
                        .modelParams(toModelParams(lcRequest))
                        .messages(lcRequest == null ? null : lcRequest.messages());
        ChatTraceContext traceContext = ChatTraceContext.fromListenerAttributes(attributes);
        if (traceContext != null) {
            builder.traceId(traceContext.getTraceId())
                    .runId(traceContext.getRunId())
                    .appId(traceContext.getAppId())
                    .appName(traceContext.getAppName())
                    .sessionId(traceContext.getSessionId())
                    .userId(traceContext.getUserId())
                    .modelId(traceContext.getModelId());
        }
        return builder;
    }

    /** 业务侧显式指定的 requestId 优先，缺省时回填厂商返回的真实响应 ID */
    private String resolveRequestId(Map<Object, Object> attributes, ChatResponse response) {
        ChatTraceContext traceContext = ChatTraceContext.fromListenerAttributes(attributes);
        if (traceContext != null && traceContext.getRequestId() != null) {
            return traceContext.getRequestId();
        }
        return response == null ? null : response.id();
    }

    /** 业务侧的模型显示名优先，缺省时回填厂商返回的模型名 */
    private String resolveModelName(Map<Object, Object> attributes, ChatResponse response) {
        ChatTraceContext traceContext = ChatTraceContext.fromListenerAttributes(attributes);
        if (traceContext != null && traceContext.getModelName() != null) {
            return traceContext.getModelName();
        }
        return response == null ? null : response.modelName();
    }

    private long resolveStartMs(Map<Object, Object> attributes) {
        Object startMs = attributes.get(KEY_START_MS);
        return startMs instanceof Long value ? value : System.currentTimeMillis();
    }

    /**
     * 流式调用首个 token 的时间戳（非流式没有这个中间点，留空而不是拿 duration 当首字延迟）。
     */
    private static Long resolveFirstTokenMs(Map<Object, Object> attributes) {
        Object firstTokenMs = attributes.get(ChatTraceContext.KEY_FIRST_TOKEN_MS);
        return firstTokenMs instanceof Long value ? value : null;
    }

    /**
     * 采样本次调用的生成参数（只记非 null 项）。
     * <p>
     * 调参与效果对不上号是排障时常碰到的问题：光看 prompt 与 token 看不出是不是 temperature 拉高导致的跑偏。
     */
    private String toModelParams(ChatRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        putIfNotNull(params, "temperature", request.temperature());
        putIfNotNull(params, "topP", request.topP());
        // LangChain4j 1.x 把 maxTokens 改名为 maxOutputTokens，对外字段名仍沿用习惯叫法
        putIfNotNull(params, "maxTokens", request.maxOutputTokens());
        putIfNotNull(params, "topK", request.topK());
        if (params.isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.toJSONString(params);
        } catch (Exception e) {
            log.warn("LLM 调用参数序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private static void putIfNotNull(Map<String, Object> params, String key, Object value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    /** 可空计数 → 可空 Long：缺项就是缺项，不猜 0，也不该因为拆箱把整条记录丢掉 */
    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
