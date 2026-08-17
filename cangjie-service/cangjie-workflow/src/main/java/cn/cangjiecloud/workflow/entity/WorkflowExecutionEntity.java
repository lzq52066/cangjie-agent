package cn.cangjiecloud.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流执行记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "workflow_execution", autoResultMap = true)
public class WorkflowExecutionEntity extends BaseEntity {

    /** 工作流 ID */
    private String workflowId;

    /** 关联应用 ID */
    private String applicationId;

    /** 会话 ID */
    private String sessionId;

    /** 状态：pending / running / completed / failed */
    private String status;

    /** 输入（JSON） */
    private String inputs;

    /** 输出（JSON） */
    private String outputs;

    /** 当前节点 */
    private String currentNode;

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
