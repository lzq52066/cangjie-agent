package cn.cangjiecloud.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeDocumentService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public R<List<KnowledgeDocumentEntity>> list(@PathVariable String knowledgeBaseId) {
        return R.data(documentService.listByKnowledgeBase(knowledgeBaseId));
    }

    @GetMapping("/{documentId}")
    public R<KnowledgeDocumentEntity> get(@PathVariable String documentId) {
        return R.data(documentService.getById(documentId));
    }

    @GetMapping("/paragraphs/{documentId}")
    public R<List<KnowledgeParagraphEntity>> paragraphs(@PathVariable String documentId) {
        return R.data(paragraphService.listByDocument(documentId));
    }
}
