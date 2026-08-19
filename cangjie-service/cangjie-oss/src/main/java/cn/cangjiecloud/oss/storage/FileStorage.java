package cn.cangjiecloud.oss.storage;

/**
 * 文件存储抽象接口。
 * <p>
 * 不同存储后端（本地磁盘、MinIO、腾讯云 COS 等）统一实现该接口。
 * </p>
 */
public interface FileStorage {

    /**
     * 上传文件，返回存储路径（相对路径，不含域名）。
     */
    String upload(byte[] data, String fileName, String contentType);

    /**
     * 根据存储路径下载文件内容。
     */
    byte[] download(String filePath);

    /**
     * 删除指定存储路径的文件。
     */
    void delete(String filePath);

    /**
     * 获取存储类型标识（如 local、minio、cos）。
     */
    String getType();

    /**
     * 获取文件的公网访问 URL，本地存储返回 null 表示无直接 URL。
     */
    String getUrl(String filePath);
}