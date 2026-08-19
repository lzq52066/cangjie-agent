package cn.cangjiecloud.application.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用版本快照
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "application_version")
public class ApplicationVersionEntity extends BaseEntity {

    /** 关联应用 ID */
    private String applicationId;

    /** 版本号，从 1 递增 */
    private Integer version;

    /** 应用名称 */
    private String name;

    /** 应用描述 */
    private String description;

    /** 应用类型 */
    private String type;

    /** 模型 ID */
    private String modelId;

    /** 知识库 ID 列表（JSON） */
    private String knowledgeBaseIds;

    /** 提示词模板 ID */
    private String promptTemplateId;

    /** 技能 ID 列表（JSON） */
    private String skillIds;

    /** 规则 ID 列表（JSON） */
    private String ruleIds;

    /** 是否启用记忆 */
    private Boolean memoryEnabled;

    /** 最大对话轮数 */
    private Integer maxTurns;

    /** 温度 */
    private Double temperature;

    /** 额外配置（JSON） */
    private String config;

    /** 建议问题（JSON） */
    private String suggestions;

    /** 应用图标 */
    private String icon;

    /** 完整配置快照（JSON） */
    private String snapshot;

    /** 发布说明 */
    private String publishLog;

    /** 发布人 */
    private String publishBy;
}