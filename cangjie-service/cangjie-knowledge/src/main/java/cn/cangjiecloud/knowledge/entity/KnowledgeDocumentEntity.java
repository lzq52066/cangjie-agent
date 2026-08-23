package cn.cangjiecloud.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_document", autoResultMap = true)
public class KnowledgeDocumentEntity extends BaseEntity {

    private String knowledgeBaseId;

    /** 文件名 */
    private String name;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件路径/OSS key */
    private String filePath;

    /** 文件 MD5 */
    private String fileMd5;

    /** 处理状态 */
    private String status;

    /** 处理消息 */
    private String processMessage;

    /** 段落数 */
    private Integer paragraphCount;

    /** Token 总数 */
    private Integer tokenCount;

    /** 文档标题 */
    private String title;

    /** 目录路径 */
    private String directoryPath;

    /** 关联的文件管理记录 ID */
    private String fileId;

    /** 文档摘要（LLM 生成，用于 two-stage 检索的第一阶段） */
    private String summary;

    /** 元数据（JSON） */
    private String metadata;
}
