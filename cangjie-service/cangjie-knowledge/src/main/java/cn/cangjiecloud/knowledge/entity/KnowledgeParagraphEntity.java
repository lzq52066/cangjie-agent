package cn.cangjiecloud.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_paragraph", autoResultMap = true)
public class KnowledgeParagraphEntity extends BaseEntity {

    private String knowledgeBaseId;
    private String documentId;

    /** 段落标题 */
    private String title;

    /** 段落内容 */
    private String content;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 页码 */
    private Integer pageNumber;

    /** Token 数 */
    private Integer tokenCount;

    /** 字符数 */
    private Integer charCount;

    /** 向量状态：pending / embedded */
    private String vectorStatus;

    /** 元数据（JSON） */
    private String metadata;
}
