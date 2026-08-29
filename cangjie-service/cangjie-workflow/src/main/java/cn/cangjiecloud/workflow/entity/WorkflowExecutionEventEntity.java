package cn.cangjiecloud.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流节点级执行事件
 * <p>
 * 每个节点执行完成落一条记录，支撑执行历史回放与断点续跑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "workflow_execution_event", autoResultMap = true)
public class WorkflowExecutionEventEntity extends BaseEntity {

    /** 所属执行记录 ID */
    private String executionId;

    /** 节点 ID */
    private String nodeId;

    /** 节点类型：start / end / llm / api / code / ... */
    private String nodeType;

    /** 状态：success / failed */
    private String status;

    /** 节点输出（JSON，超长截断） */
    private String outputs;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
