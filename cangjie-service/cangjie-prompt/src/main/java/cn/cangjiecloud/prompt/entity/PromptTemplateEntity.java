package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "prompt_template", autoResultMap = true)
public class PromptTemplateEntity extends BaseEntity {

    /** 模板名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 模板内容 */
    private String content;

    /** 描述 */
    private String description;

    /** 变量列表（JSON） */
    private String variables;

    /** 是否默认模板 */
    private Boolean isDefault;

    /** 状态：active / inactive */
    private String status;
}
