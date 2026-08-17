package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "command", autoResultMap = true)
public class CommandEntity extends BaseEntity {

    /** 命令名称 */
    private String name;

    /** 命令标识 */
    private String command;

    /** 描述 */
    private String description;

    /** 类型 */
    private String type;

    /** 脚本 */
    private String script;

    /** 参数（JSON） */
    private String parameters;

    /** 状态：active / inactive */
    private String status;
}
