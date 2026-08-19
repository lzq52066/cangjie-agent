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

    /** 区域，如 us-east-1 */
    private String region;

    /** 访问密钥 */
    private String accessKey;

    /** 私密密钥 */
    private String secretKey;

    /** 默认存储桶名称 */
    private String bucket;

    /** 公网访问地址（替换 endpoint 中的内网地址），如 https://cdn.example.com */
    private String publicEndpoint;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }
}