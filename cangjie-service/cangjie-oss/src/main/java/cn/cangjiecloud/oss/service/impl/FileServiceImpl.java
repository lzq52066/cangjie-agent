package cn.cangjiecloud.oss.service.impl;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.oss.storage.FileStorage;
import cn.cangjiecloud.oss.entity.FileEntity;
import cn.cangjiecloud.oss.mapper.FileMapper;
import cn.cangjiecloud.oss.service.IFileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, FileEntity> implements IFileService {

    private final FileStorage fileStorage;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity upload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("文件不能为空");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            throw new ApiException("读取上传文件失败: " + e.getMessage());
        }

        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();
        String storedPath = fileStorage.upload(bytes, originalName, contentType);

        FileEntity entity = new FileEntity();
        entity.setFileName(originalName);
        entity.setStorageType(fileStorage.getType());
        entity.setFilePath(storedPath);
        entity.setFileSize(file.getSize());
        entity.setContentType(contentType);
        entity.setMd5(md5(bytes));
        entity.setCategory(StringUtils.hasText(category) ? category : "other");
        entity.setUploaderId(UserContext.getUserId());
        entity.setStatus("active");
        save(entity);

        // 对象存储（MinIO/COS）返回公开 URL，本地存储用后端下载接口
        String publicUrl = fileStorage.getUrl(storedPath);
        entity.setUrl(publicUrl != null ? publicUrl : AppConst.ADMIN_API + "/file/" + entity.getId());
        updateById(entity);
        log.info("文件上传成功: id={}, path={}, url={}", entity.getId(), storedPath, entity.getUrl());
        return entity;
    }

    @Override
    public byte[] download(String id) {
        FileEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("文件不存在");
        }
        if (!"active".equals(entity.getStatus())) {
            throw new ApiException("文件已被删除");
        }
        return fileStorage.download(entity.getFilePath());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        FileEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("文件不存在");
        }
        fileStorage.delete(entity.getFilePath());
        entity.setStatus("deleted");
        updateById(entity);
        log.info("文件已删除: id={}", id);
    }

    @Override
    public IPage<FileEntity> pageQuery(String keyword, String category, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getStatus, "active")
                .eq(StringUtils.hasText(category), FileEntity::getCategory, category)
                .like(StringUtils.hasText(keyword), FileEntity::getFileName, keyword)
                .orderByDesc(FileEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    private String md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 计算失败: {}", e.getMessage());
            return null;
        }
    }
}
