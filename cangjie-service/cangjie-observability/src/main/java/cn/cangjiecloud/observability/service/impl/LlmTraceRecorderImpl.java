package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.context.TraceContext;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceRecorder;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import cn.cangjiecloud.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * LLM 调用追踪记录器实现
 * <p>
 * 提供统一的 trace 写入 API，支持从 TraceContext 自动获取 traceId/sessionId/userId，
 * 并支持可配置的脱敏开关。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTraceRecorderImpl implements ILlmTraceRecorder {

    private final ILlmTraceService llmTraceService;

    /** 是否开启脱敏（默认 false，全量明文存储） */
    @Value("${cangjie.observability.llm-trace.mask.enabled:false}")
    private boolean maskEnabled;

    /** 是否存储完整 prompt/response 内容（默认 true） */
    @Value("${cangjie.observability.llm-trace.store-content:true}")
    private boolean storeContent;

    /** 内容最大存储长度（字符数，0 表示不限制），超长自动截断 */
    @Value("${cangjie.observability.llm-trace.max-content-length:20000}")
    private int maxContentLength;

    @Override
    public void recordSuccess(LlmTraceRecord record) {
        save(record, "success", null);
    }

    @Override
    public void recordFailure(LlmTraceRecord record, String errorMessage) {
        save(record, "fail", errorMessage);
    }

    private void save(LlmTraceRecord record, String status, String errorMessage) {
        try {
            LlmTraceEntity trace = new LlmTraceEntity();
            trace.setTraceId(resolveTraceId(record));
            trace.setRequestId(record.getRequestId());
            trace.setAppId(record.getAppId());
            trace.setAppName(record.getAppName());
            trace.setSessionId(resolveSessionId(record));
            trace.setUserId(resolveUserId(record));
            trace.setModelId(record.getModelId());
            trace.setModelName(record.getModelName());

            // promptContent
            String promptContent = record.getPromptContent();
            if (promptContent == null && record.getMessages() != null) {
                promptContent = JsonUtils.toJSONString(record.getMessages());
            }
            trace.setPromptContent(applyContentPolicy(promptContent));

            trace.setInputTokens(record.getInputTokens());
            trace.setOutputTokens(record.getOutputTokens());
            trace.setTotalTokens(record.getTotalTokens());
            trace.setResponseContent(applyContentPolicy(record.getResponseContent()));
            trace.setFinishReason(record.getFinishReason());
            trace.setDuration(record.getDuration());
            trace.setStatus(status);
            trace.setErrorMessage(errorMessage);
            trace.setStartTime(record.getStartTimeMs() != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getStartTimeMs()), ZoneId.systemDefault())
                    : LocalDateTime.now());

            llmTraceService.save(trace);
        } catch (Exception ex) {
            log.warn("LLM trace 记录失败: {}", ex.getMessage());
        }
    }

    /**
     * 根据配置对内容做脱敏/截断处理
     */
    private String applyContentPolicy(String content) {
        if (content == null) return null;
        if (!storeContent) {
            // 不存完整内容，保留前 200 字符作为摘要
            return content.length() > 200 ? content.substring(0, 200) + "...[truncated]" : content;
        }
        if (maskEnabled) {
            content = maskSensitiveContent(content);
        }
        if (maxContentLength > 0 && content.length() > maxContentLength) {
            content = content.substring(0, maxContentLength) + "...[truncated]";
        }
        return content;
    }

    /**
     * 脱敏处理：替换敏感关键词
     */
    private String maskSensitiveContent(String content) {
        if (content == null) return null;
        return content
                // JSON 字段脱敏
                .replaceAll("\"(apiKey|api_key|apikey)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"******\"")
                .replaceAll("\"(password|passwd|secret|token|authorization)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"******\"")
                // 手机号脱敏
                .replaceAll("1[3-9]\\d{9}", "******")
                // 身份证脱敏
                .replaceAll("\\d{17}[\\dXx]", "******");
    }

    private String resolveTraceId(LlmTraceRecord record) {
        if (record.getTraceId() != null) return record.getTraceId();
        return TraceContext.getTraceId();
    }

    private String resolveSessionId(LlmTraceRecord record) {
        if (record.getSessionId() != null) return record.getSessionId();
        return TraceContext.getSessionId();
    }

    private String resolveUserId(LlmTraceRecord record) {
        if (record.getUserId() != null) return record.getUserId();
        return TraceContext.getUserId();
    }
}