package cn.cangjiecloud.oss.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置属性。
 * <p>
 * 当 cangjie.oss.type=minio 时生效，本地存储无需配置。
 * </p>
 */
@ConfigurationProperties(prefix = "cangjie.oss.minio")
public class MinioProperties {

    /** MinIO 服务地址，如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 私密密钥 */
    private String secretKey;

    /** 默认存储桶名称 */
    private String bucketName;

    /** 是否使用 HTTPS */
    private Boolean secure = false;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public Boolean getSecure() {
        return secure;
    }

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }
}