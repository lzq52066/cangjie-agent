package cn.cangjiecloud.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeDocumentService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/knowledge/document")
public class KnowledgeDocumentController {

    private final IKnowledgeDocumentService documentService;
    private final IKnowledgeParagraphService paragraphService;

    @PostMapping("/upload/{knowledgeBaseId}")
    public R<KnowledgeDocumentEntity> upload(@PathVariable String knowledgeBaseId,
                                              @RequestParam("file") MultipartFile file) {
        return R.data(documentService.upload(knowledgeBaseId, file));
    }

    @DeleteMapping("/{documentId}")
    public R<Void> delete(@PathVariable String documentId) {
        documentService.delete(documentId);
        return R.ok();
    }

    @GetMapping("/list/{knowledgeBaseId}")
    public R<PageResult<KnowledgeDocumentEntity>> list(@PathVariable String knowledgeBaseId,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(documentService.pageQuery(knowledgeBaseId, pageNum, pageSize)));
    }

    @GetMapping("/{documentId}")
    public R<KnowledgeDocumentEntity> get(@PathVariable String documentId) {
        return R.data(documentService.getById(documentId));
    }

    @GetMapping("/paragraphs/{documentId}")
    public R<PageResult<KnowledgeParagraphEntity>> paragraphs(@PathVariable String documentId,
                                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(paragraphService.pageQuery(documentId, pageNum, pageSize)));
    }

    @PostMapping("/re-embed/{documentId}")
    public R<Void> reEmbed(@PathVariable String documentId) {
        documentService.reEmbed(documentId);
        return R.ok("文档重新向量化已完成");
    }
}
