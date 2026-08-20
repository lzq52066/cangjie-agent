package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * LLM 调用明细追踪实体（可观测性）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "llm_trace", autoResultMap = true)
public class LlmTraceEntity extends BaseEntity {

    /** 链路 ID（关联 trace_record） */
    private String traceId;

    /** OpenAI request ID（如 chatcmpl-xxx） */
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

    /** 模型返回内容（完整回复） */
    private String responseContent;

    /** 完成原因（如 stop、length 等） */
    private String finishReason;

    /** 耗时（毫秒） */
    private Long duration;

    /** 状态：success / fail */
    private String status;

    /** 错误信息（失败时记录） */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startTime;
}