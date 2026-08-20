package cn.cangjiecloud.application.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能应用实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "application", autoResultMap = true)
public class ApplicationEntity extends BaseEntity {

    /** 应用名称 */
    private String name;

    /** 应用描述 */
    private String description;

    /** 应用类型：chat / agent / workflow */
    private String type;

    /** 关联模型 ID */
    private String modelId;

    /** 知识库 ID 列表（JSON 数组） */
    private String knowledgeBaseIds;

    /** 提示词模板 ID */
    private String promptTemplateId;

    /** 技能 ID 列表（JSON 数组） */
    private String skillIds;

    /** 规则 ID 列表（JSON 数组） */
    private String ruleIds;

    /** 工具 ID 列表（JSON 数组） */
    private String toolIds;

    /** 是否启用记忆 */
    private Boolean memoryEnabled;

    /** 最大对话轮数 */
    private Integer maxTurns;

    /** 温度参数 */
    private Double temperature;

    /** 额外配置（JSON） */
    private String config;

    /** 建议问题列表（JSON 数组，用于 chat 入口欢迎页） */
    private String suggestions;

    /** 图标 */
    private String icon;

    /** 状态：draft / published */
    private String status;

    /** 对外 API Key */
    private String apikey;
}
