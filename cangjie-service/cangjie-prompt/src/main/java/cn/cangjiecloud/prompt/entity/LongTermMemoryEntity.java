package cn.cangjiecloud.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 长期记忆（用户 + 应用维度的画像信息）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "long_term_memory")
public class LongTermMemoryEntity extends BaseEntity {

    /** 用户 ID */
    private String userId;

    /** 关联应用 ID */
    private String applicationId;

    /** 记忆维度：preference / background / convention / goal */
    private String dimension;

    /** 记忆内容 */
    private String content;

    /** 置信度 */
    private Double confidence;

    /** 来源：inferred（推断）/ explicit（显式）/ import（导入） */
    private String source;

    /** 触发次数 */
    private Integer triggerCount;

    /** 最后触发时间 */
    private String lastTriggeredAt;

    /** 是否激活 */
    private Boolean isActive;
}