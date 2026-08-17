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

    /** 切片策略：smart（智能分段）/ custom（自定义分段） */
    private String splitStrategy;

    /** 段落最大字符数（custom 模式使用） */
    private Integer chunkSize;

    /** 自定义分隔符列表（JSON 数组字符串，如 ["h2","blank_line"]） */
    private String separators;

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
