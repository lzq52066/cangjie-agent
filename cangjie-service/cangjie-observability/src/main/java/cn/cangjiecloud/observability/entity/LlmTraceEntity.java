package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
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

    /** 所属 Agent 执行 run ID（关联 agent_run）；非 Harness 调用为空 */
    private String runId;

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

    /** 本次调用参数（JSON：temperature / topP / maxTokens） */
    private String modelParams;

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

    /**
     * 首字延迟（毫秒）：仅流式调用有值。
     * <p>
     * duration 在流式场景是「整段生成完」的时间，用它评估体验会把长回答误判成很慢；
     * 用户等的是第一个字，那个数只能单列。
     */
    private Long ttft;

    /** 本次调用成本（元）：按 model_config 单价 × token 估算，没定价则空 */
    private BigDecimal cost;

    /** 是否已被在线评测打过标 */
    private Boolean evaluated;

    /** 在线评测得分（JSON：指标名 → 0~1 分数），趋势直接按此列聚合 */
    private String evalScores;

    /** 状态：success / fail */
    private String status;

    /** 错误信息（失败时记录） */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startTime;
}