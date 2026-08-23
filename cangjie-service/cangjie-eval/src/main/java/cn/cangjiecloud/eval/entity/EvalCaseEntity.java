package cn.cangjiecloud.eval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评估用例实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eval_case")
public class EvalCaseEntity extends BaseEntity {

    /** 所属数据集 ID */
    private String datasetId;

    /** 问题 */
    private String question;

    /** 期望回答 */
    private String expectedAnswer;

    /** 参考文档 ID 列表（JSON 数组，用于召回率计算） */
    private String referenceDocs;
}