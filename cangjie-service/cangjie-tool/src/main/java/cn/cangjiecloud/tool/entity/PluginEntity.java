package cn.cangjiecloud.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "plugin_instance", autoResultMap = true)
public class PluginEntity extends BaseEntity {

    /** 插件名称 */
    private String name;

    /** 插件类型：tool / function / channel / parser / processor */
    private String type;

    /** 插件描述 */
    private String description;

    /** 实现类全限定类名 */
    private String className;

    /** 版本 */
    private String version;

    /** 配置（JSON） */
    private String config;

    /** 状态：active / inactive */
    private String status;

    /** 是否已加载 */
    private Boolean loaded;

    /** 加载错误信息 */
    private String loadError;
}
