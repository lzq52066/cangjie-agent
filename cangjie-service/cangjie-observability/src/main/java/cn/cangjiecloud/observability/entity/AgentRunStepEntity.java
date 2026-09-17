package cn.cangjiecloud.observability.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 执行步骤明细（增量留痕）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "agent_run_step", autoResultMap = true)
public class AgentRunStepEntity extends BaseEntity {

    private String runId;

    private Integer stepNo;

    private Integer round;

    /** llm / tool / context / hook / error */
    private String type;

    /** 模型名或函数名 */
    private String name;

    private String status;

    /** 入参（llm 只记本轮增量） */
    private String input;

    private String output;

    private String errorMessage;

    private Long inputTokens;

    private Long outputTokens;

    private Long duration;

    /** 输出是否被截断：0 否 / 1 是 */
    private Integer truncated;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
