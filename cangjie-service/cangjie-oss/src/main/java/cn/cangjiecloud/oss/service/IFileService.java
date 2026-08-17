package cn.cangjiecloud.oss.service;

import cn.cangjiecloud.oss.entity.FileEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务
 */
public interface IFileService extends IService<FileEntity> {

    /**
     * 上传文件（存储 + 记录元数据）
     */
    FileEntity upload(MultipartFile file, String category);

    /**
     * 下载文件字节
     */
    byte[] download(String id);

    /**
     * 删除文件
     */
    void delete(String id);

    /**
     * 分页查询文件列表
     */
    IPage<FileEntity> pageQuery(String keyword, String category, Integer pageNum, Integer pageSize);
}
