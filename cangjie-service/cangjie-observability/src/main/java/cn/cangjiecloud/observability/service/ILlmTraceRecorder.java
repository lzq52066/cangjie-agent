package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.entity.LlmTraceEntity;

import java.util.List;

/**
 * LLM 调用追踪记录器 — 统一的服务层 API，供所有模型调用场景使用
 */
public interface ILlmTraceRecorder {

    /**
     * 记录一次同步 LLM 调用成功的 trace
     */
    void recordSuccess(LlmTraceRecord record);

    /**
     * 记录一次 LLM 调用失败的 trace
     */
    void recordFailure(LlmTraceRecord record, String errorMessage);

    /**
     * LLM 调用记录参数对象
     */
    @lombok.Data
    @lombok.Builder
    class LlmTraceRecord {
        /** 链路 ID（可选，不传时从 TraceContext 获取） */
        private String traceId;
        /** OpenAI request ID */
        private String requestId;
        /** 应用 ID */
        private String appId;
        /** 应用名称 */
        private String appName;
        /** 会话 ID（可选，不传时从 TraceContext 获取） */
        private String sessionId;
        /** 用户 ID（可选，不传时从 TraceContext 获取） */
        private String userId;
        /** 模型 ID */
        private String modelId;
        /** 模型名称 */
        private String modelName;
        /** 提示词内容（完整消息 JSON） */
        private String promptContent;
        /** 输入 token 数 */
        private Long inputTokens;
        /** 输出 token 数 */
        private Long outputTokens;
        /** 总 token 数 */
        private Long totalTokens;
        /** 模型返回内容 */
        private String responseContent;
        /** 完成原因 */
        private String finishReason;
        /** 耗时（毫秒） */
        private Long duration;
        /** 开始时间戳（毫秒） */
        private Long startTimeMs;
        /** 消息列表（与 promptContent 二选一，List 会被序列化为 JSON） */
        private List<?> messages;
    }
}