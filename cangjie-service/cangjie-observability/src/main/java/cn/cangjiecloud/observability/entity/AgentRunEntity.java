package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 执行主记录（一次 Harness run）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_run", autoResultMap = true)
public class AgentRunEntity extends BaseEntity {

    /** 链路 ID */
    private String traceId;

    private String sessionId;

    private String appId;

    private String appName;

    private String userId;

    private String modelId;

    private String modelName;

    /** 执行形态：chat / workflow / subagent / debug */
    private String harnessType;

    /** 父 run ID */
    private String parentRunId;

    /** 嵌套深度 */
    private Integer depth;

    /** running / completed / failed / cancelled / waiting_approval */
    private String status;

    private Integer rounds;

    private Integer toolCallCount;

    private Long inputTokens;

    private Long outputTokens;

    private Long totalTokens;

    /** token 预算，NULL 表示不限制 */
    private Long tokenBudget;

    /** 成本估算 */
    private BigDecimal estimatedCost;

    private String finishReason;

    private String finalText;

    private String errorMessage;

    /** ResumeState JSON */
    private String contextSnapshot;

    /** 上下文槽位占用（摘要 JSON） */
    private String contextUsage;

    /** 一次性恢复令牌 */
    private String resumeToken;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;
}
