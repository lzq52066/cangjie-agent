package cn.cangjiecloud.core.oss;

/**
 * 文件存储 SPI。
 * <p>
 * 内置实现为本地磁盘存储（LocalFileStorage），预留该接口以便未来对接
 * 阿里云 OSS / 腾讯云 COS / MinIO 等对象存储。
 */
public interface FileStorage {

    /**
     * 上传文件，返回可访问的存储路径（相对路径 / OSS key）
     */
    String upload(byte[] data, String fileName, String contentType);

    /**
     * 下载文件字节
     */
    byte[] download(String filePath);

    /**
     * 删除文件
     */
    void delete(String filePath);

    /**
     * 存储类型：local / aliyun / cos / minio
     */
    String getType();
}
