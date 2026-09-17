package cn.cangjiecloud.tool.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "tool", autoResultMap = true)
public class ToolEntity extends BaseEntity {

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具类型：tool / function / api（旧版兼容） */
    private String type;

    /** 工具子类型：HTTP / CUSTOM / MCP / SKILL / PLUGIN（新版策略分发用） */
    private String toolType;

    /** 函数名（function calling 用） */
    private String functionName;

    /** 参数定义（JSON Schema，用于 function calling） */
    private String parameters;

    /** 实现类全限定类名（PLUGIN 类型使用） */
    private String implementation;

    /** 工具代码/脚本内容（CUSTOM 类型）或配置（JSON） */
    private String config;

    /** 状态：active / inactive */
    private String status;

    /** 图标 */
    private String icon;

    /** 分类 */
    private String category;

    /** 风险等级：low / medium / high */
    private String riskLevel;

    /** 调用前是否必须人工审批：0 否 / 1 是 */
    private Integer requireApproval;

    /** 单次执行超时（秒），null 用全局默认 */
    private Integer timeoutSeconds;

    /** 输出回喂模型前的最大字符数，null 用全局默认 */
    private Integer maxOutputChars;
}
