package cn.cangjiecloud.oss.storage;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.oss.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现（部署约束下的最小方案）。
 * <p>
 * 文件按 yyyy/MM/uuid.ext 结构存放到配置目录 cangjie.oss.local-path。
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${cangjie.oss.local-path:./data/files}")
    private String localPath;

    @Override
    public String upload(byte[] data, String fileName, String contentType) {
        String datePath = LocalDate.now().format(DATE_PATH);
        String storedName = buildStoredName(fileName);
        try {
            Path dir = Path.of(localPath, datePath);
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            Files.write(target, data);
            return datePath + "/" + storedName;
        } catch (IOException e) {
            log.error("文件存储失败: {}", e.getMessage(), e);
            throw new ApiException("文件存储失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String filePath) {
        Path file = resolve(filePath);
        if (file == null || !Files.exists(file)) {
            throw new ApiException("文件不存在: " + filePath);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("文件读取失败: {}", e.getMessage(), e);
            throw new ApiException("文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String filePath) {
        Path file = resolve(filePath);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("文件删除失败: {} - {}", filePath, e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "local";
    }

    /**
     * 生成存储文件名：uuid + 原扩展名（防止路径穿越与文件名冲突）
     */
    private String buildStoredName(String fileName) {
        String ext = "";
        if (fileName != null) {
            int idx = fileName.lastIndexOf('.');
            if (idx >= 0 && idx < fileName.length() - 1) {
                ext = fileName.substring(idx).toLowerCase(Locale.ROOT);
            }
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }

    /**
     * 拼接并规范化文件路径，防止 ../ 路径穿越
     */
    private Path resolve(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return Path.of(localPath).resolve(filePath).normalize();
    }
}
