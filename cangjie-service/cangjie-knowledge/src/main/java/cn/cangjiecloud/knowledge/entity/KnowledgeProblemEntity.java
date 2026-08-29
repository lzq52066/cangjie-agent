package cn.cangjiecloud.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库问题（常见问题）：与段落多对多关联，检索时作为问题路召回入口
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_problem", autoResultMap = true)
public class KnowledgeProblemEntity extends BaseEntity {

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 问题内容 */
    private String content;

    /** 来源：manual（人工创建）/ ai（AI 生成） */
    private String source;
}
