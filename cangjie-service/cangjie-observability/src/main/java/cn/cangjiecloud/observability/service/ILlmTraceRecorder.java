package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import dev.langchain4j.data.message.ChatMessage;

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
        /** 所属 Agent 执行 run ID（可选，非 Harness 场景为空） */
        private String runId;
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
        /** 本次调用参数（JSON：temperature / topP / maxTokens） */
        private String modelParams;
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
        /**
         * 首个 token 到达时间戳（毫秒），仅流式调用携带。
         * <p>
         * 与 {@code startTimeMs} 相减即首字延迟；同步调用没有这个中间点，留空而不是拿 duration 顶上去。
         */
        private Long firstTokenMs;
        /** 开始时间戳（毫秒） */
        private Long startTimeMs;
        /** 请求消息列表（与 promptContent 二选一，List 会被序列化为 JSON） */
        private List<ChatMessage> messages;
    }
}