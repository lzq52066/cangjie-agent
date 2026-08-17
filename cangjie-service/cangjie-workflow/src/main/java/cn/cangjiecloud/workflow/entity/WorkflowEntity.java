package cn.cangjiecloud.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "workflow", autoResultMap = true)
public class WorkflowEntity extends BaseEntity {

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 节点定义（JSON） */
    private String nodes;

    /** 边定义（JSON） */
    private String edges;

    /** 变量定义（JSON） */
    private String variables;

    /** 版本号 */
    private Integer version;

    /** 状态：draft / published */
    private String status;

    /** 关联应用 ID */
    private String applicationId;
}
