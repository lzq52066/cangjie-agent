package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "skill", autoResultMap = true)
public class SkillEntity extends BaseEntity {

    /** 技能名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 类型 */
    private String type;

    /** 内容 */
    private String content;

    /** 函数名 */
    private String functionName;

    /** 参数（JSON） */
    private String parameters;

    /** 状态：active / inactive */
    private String status;
}
