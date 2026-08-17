package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "rule", autoResultMap = true)
public class RuleEntity extends BaseEntity {

    /** 规则名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 类型 */
    private String type;

    /** 条件 */
    private String condition;

    /** 动作 */
    private String action;

    /** 优先级 */
    private Integer priority;

    /** 状态：active / inactive */
    private String status;
}
