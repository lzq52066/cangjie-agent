package cn.cangjiecloud.oss.entity;

import cn.cangjiecloud.common.mp.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "file_record", autoResultMap = true)
public class FileEntity extends BaseEntity {

    /** 原始文件名 */
    private String fileName;

    /** 存储类型：local / aliyun / cos / minio */
    private String storageType;

    /** 存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 */
    private String contentType;

    /** 文件 MD5 */
    private String md5;

    /** 访问 URL */
    private String url;

    /** 分类：avatar / document / image / other */
    private String category;

    /** 上传人 ID */
    private String uploaderId;

    /** 状态：active / deleted */
    private String status;
}
