package cn.cangjiecloud.oss.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地文件存储实现。
 * <p>
 * 文件按日期分目录存放，避免单目录文件过多。
 * 当 cangjie.oss.type=local（或不配置）时启用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cangjie.oss.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    @Value("${cangjie.oss.local-path:./data/files}")
    private String localPath;

    private Path rootPath;

    @PostConstruct
    public void init() {
        rootPath = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("无法创建本地文件存储目录: " + rootPath, e);
        }
        log.info("LocalFileStorage 初始化完成，根目录: {}", rootPath);
    }

    @Override
    public String upload(byte[] data, String fileName, String contentType) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path targetDir = rootPath.resolve(dateDir);
        Path targetFile = targetDir.resolve(storedName);

        try {
            Files.createDirectories(targetDir);
            Files.write(targetFile, data);
            log.debug("本地文件已保存: {}", targetFile);
        } catch (IOException e) {
            throw new RuntimeException("本地文件写入失败: " + targetFile, e);
        }

        return dateDir + "/" + storedName;
    }

    @Override
    public byte[] download(String filePath) {
        Path fullPath = rootPath.resolve(filePath).normalize();
        if (!fullPath.startsWith(rootPath)) {
            throw new SecurityException("非法文件路径: " + filePath);
        }
        try {
            return Files.readAllBytes(fullPath);
        } catch (IOException e) {
            throw new RuntimeException("本地文件读取失败: " + fullPath, e);
        }
    }

    @Override
    public void delete(String filePath) {
        Path fullPath = rootPath.resolve(filePath).normalize();
        if (!fullPath.startsWith(rootPath)) {
            throw new SecurityException("非法文件路径: " + filePath);
        }
        try {
            Files.deleteIfExists(fullPath);
            log.debug("本地文件已删除: {}", fullPath);
        } catch (IOException e) {
            log.warn("本地文件删除失败: {}", fullPath, e);
        }
    }

    @Override
    public String getType() {
        return "local";
    }

    @Override
    public String getUrl(String filePath) {
        // 本地存储无公网 URL，由 FileServiceImpl 回退到 /file/{id} 下载接口
        return null;
    }
}