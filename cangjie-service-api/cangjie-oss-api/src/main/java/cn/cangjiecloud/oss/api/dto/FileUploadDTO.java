package cn.cangjiecloud.oss.api.dto;

import lombok.Data;

/**
 * 文件上传参数
 */
@Data
public class FileUploadDTO {

    /** 原始文件名 */
    private String fileName;

    /** 内容类型 */
    private String contentType;

    /** 分类：avatar / document / image / other */
    private String category;
}
