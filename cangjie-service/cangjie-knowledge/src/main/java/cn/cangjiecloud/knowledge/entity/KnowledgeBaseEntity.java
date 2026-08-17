package cn.cangjiecloud.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_base", autoResultMap = true)
public class KnowledgeBaseEntity extends BaseEntity {

    private String name;
    private String description;

    /** 切片策略：sentence / structural / token */
    private String splitStrategy;

    /** 切片大小（字符数） */
    private Integer chunkSize;

    /** 切片重叠量 */
    private Integer chunkOverlap;

    /** 嵌入模型 ID */
    private String embeddingModelId;

    /** 嵌入维度 */
    private Integer embeddingDimension;

    /** 文档数量 */
    private Integer documentCount;

    /** 段落数量 */
    private Integer paragraphCount;

    /** 目录结构（JSON） */
    private String directory;

    /** 状态 */
    private String status;
}
