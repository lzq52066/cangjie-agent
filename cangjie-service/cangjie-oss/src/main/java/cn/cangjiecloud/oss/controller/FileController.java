package cn.cangjiecloud.oss.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.oss.entity.FileEntity;
import cn.cangjiecloud.oss.service.IFileService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件管理接口
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/file")
public class FileController {

    private final IFileService fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public R<FileEntity> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String category) {
        return R.data(fileService.upload(file, category));
    }

    /**
     * 预览 / 下载文件。
     * ?download=true 下载（attachment，保留原文件名），否则浏览器内联预览（inline）。
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id,
                                           @RequestParam(defaultValue = "false") boolean download) {
        FileEntity entity = fileService.getById(id);
        byte[] data = fileService.download(id);
        String contentType = entity != null && entity.getContentType() != null
                ? entity.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String fileName = entity != null && entity.getFileName() != null
                ? entity.getFileName() : "file";
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = download
                ? "attachment; filename*=UTF-8''" + encodedName
                : "inline; filename*=UTF-8''" + encodedName;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        fileService.delete(id);
        return R.ok();
    }

    /**
     * 文件列表
     */
    @GetMapping
    public R<IPage<FileEntity>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(fileService.pageQuery(keyword, category, pageNum, pageSize));
    }
}
