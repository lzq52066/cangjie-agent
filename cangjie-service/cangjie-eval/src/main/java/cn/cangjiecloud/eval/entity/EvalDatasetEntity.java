package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评估数据集实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eval_dataset")
public class EvalDatasetEntity extends BaseEntity {

    /** 数据集名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 关联知识库 ID */
    private String knowledgeBaseId;

    /** 用例数量（冗余） */
    private Integer caseCount;
}