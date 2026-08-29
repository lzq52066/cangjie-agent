package cn.cangjiecloud.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问题-段落关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "problem_paragraph", autoResultMap = true)
public class ProblemParagraphEntity extends BaseEntity {

    /** 问题 ID */
    private String problemId;

    /** 段落 ID */
    private String paragraphId;

    /** 冗余知识库 ID（便于按库清理） */
    private String knowledgeBaseId;
}
