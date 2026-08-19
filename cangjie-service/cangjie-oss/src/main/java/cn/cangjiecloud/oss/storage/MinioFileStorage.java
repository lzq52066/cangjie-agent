package cn.cangjiecloud.oss.storage;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现。
 * <p>
 * 当 cangjie.oss.type=minio 时启用。
 * 文件路径格式：{yyyy/MM/dd}/{uuid}.{ext}
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cangjie.oss.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    private final MinioProperties properties;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());
        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(properties.getRegion());
        }
        minioClient = builder.build();

        String bucket = properties.getBucket();
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 存储桶已创建: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO 初始化失败（bucket=" + bucket + "）: " + e.getMessage(), e);
        }
        log.info("MinioFileStorage 初始化完成，endpoint={}, bucket={}", properties.getEndpoint(), bucket);
    }

    @Override
    public String upload(byte[] data, String fileName, String contentType) {
        String dateDir = new java.text.SimpleDateFormat("yyyy/MM/dd")
                .format(new java.util.Date());
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf("."));
        }
        String objectName = dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        try (InputStream is = new ByteArrayInputStream(data)) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(is, data.length, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();
            minioClient.putObject(args);
            log.debug("MinIO 文件上传成功: {}", objectName);
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件上传失败: " + objectName, e);
        }

        return objectName;
    }

    @Override
    public byte[] download(String filePath) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(properties.getBucket()).object(filePath).build())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = stream.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件下载失败: " + filePath, e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(properties.getBucket()).object(filePath).build());
            log.debug("MinIO 文件已删除: {}", filePath);
        } catch (Exception e) {
            log.warn("MinIO 文件删除失败: {}", filePath, e);
        }
    }

    public String presignedUrl(String filePath) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucket())
                            .object(filePath)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
            String publicEp = properties.getPublicEndpoint();
            if (publicEp != null && !publicEp.isBlank()) {
                String ep = properties.getEndpoint().replaceAll("/+$", "");
                url = url.replace(ep, publicEp.replaceAll("/+$", ""));
            }
            return url;
        } catch (Exception e) {
            log.warn("生成 MinIO 预签名 URL 失败: {}", filePath, e);
            return null;
        }
    }

    @Override
    public String getUrl(String filePath) {
        return presignedUrl(filePath);
    }

    @Override
    public String getType() {
        return "minio";
    }
}