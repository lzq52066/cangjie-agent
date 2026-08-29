package cn.cangjiecloud.application.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用模板：从现有应用沉淀的可复用配置快照，支持一键创建新应用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "application_template", autoResultMap = true)
public class ApplicationTemplateEntity extends BaseEntity {

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 图标 */
    private String icon;

    /** 分类（如：客服 / 写作 / 分析） */
    private String category;

    /** 源应用类型：chat / agent / workflow */
    private String appType;

    /** 应用配置快照（JSON，不含 ID/API Key/统计数据） */
    private String snapshot;

    /** 使用次数 */
    private Integer useCount;

    /** 状态：published / offline */
    private String status;
}
