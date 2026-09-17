package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.oss.service.IFileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.api.enums.DocumentStatus;
import cn.cangjiecloud.knowledge.api.enums.DocumentType;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.service.DocumentProcessingService;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeDocumentService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl
        extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentEntity>
        implements IKnowledgeDocumentService {

    private final IKnowledgeBaseService knowledgeBaseService;
    private final IKnowledgeParagraphService paragraphService;
    private final VectorStore vectorStore;
    private final IFileService fileService;
    private final DocumentProcessingService documentProcessingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentEntity upload(String knowledgeBaseId, MultipartFile file) {
        knowledgeBaseService.checkAccess(knowledgeBaseId);
        KnowledgeBaseEntity kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null) {
            throw new ApiException("知识库不存在");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException("读取文件失败: " + e.getMessage());
        }

        // 0. 先通过文件管理统一服务保存原件（记录到 file_record，存储到 local/MinIO）
        var fileEntity = fileService.upload(file, "document");

        // 1. 创建知识文档记录
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setName(file.getOriginalFilename());
        doc.setFileType(DocumentType.of(file.getOriginalFilename()).getCode());
        doc.setFileSize(file.getSize());
        doc.setTitle(file.getOriginalFilename());
        doc.setFileId(fileEntity.getId());
        doc.setStatus(DocumentStatus.PENDING.getCode());
        try {
            doc.setFileMd5(md5(fileBytes));
        } catch (Exception e) {
            log.warn("文件 MD5 计算失败: {}", e.getMessage());
        }
        save(doc);

        // 2. 异步处理：事务提交后触发（避免异步线程读不到未提交的文档记录），
        //    处理进度通过文档状态字段查询，失败可通过重新处理恢复
        String documentId = doc.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessingService.processAsync(documentId, fileBytes);
            }
        });

        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String documentId) {
        KnowledgeDocumentEntity doc = getById(documentId);
        if (doc == null) return;
        knowledgeBaseService.checkAccess(doc.getKnowledgeBaseId());

        // 删除向量
        vectorStore.deleteByDocument(documentId);
        // 删除段落
        paragraphService.deleteByDocument(documentId);
        // 删除文档
        removeById(documentId);

        // 更新知识库计数
        documentProcessingService.updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
        log.info("文档已删除: {} ({})", doc.getName(), documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocess(String documentId) {
        KnowledgeDocumentEntity doc = getById(documentId);
        if (doc == null) {
            throw new ApiException("文档不存在");
        }
        if (!DocumentStatus.FAILED.getCode().equals(doc.getStatus())) {
            throw new ApiException("仅失败状态的文档可重新处理");
        }
        knowledgeBaseService.checkAccess(doc.getKnowledgeBaseId());

        log.info("重新处理文档: {} ({})", doc.getName(), documentId);
        reEmbedDocument(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reEmbed(String documentId) {
        KnowledgeDocumentEntity doc = getById(documentId);
        if (doc == null) {
            throw new ApiException("文档不存在");
        }
        knowledgeBaseService.checkAccess(doc.getKnowledgeBaseId());
        log.info("重新向量化文档: {} ({})", doc.getName(), documentId);
        reEmbedDocument(doc);
    }

    /**
     * 重新向量化文档：删除旧向量，对已有段落重新生成向量
     */
    @Transactional(rollbackFor = Exception.class)
    public void reEmbedDocument(KnowledgeDocumentEntity doc) {
        String documentId = doc.getId();
        List<KnowledgeParagraphEntity> paragraphs = paragraphService.listByDocument(documentId);
        if (paragraphs.isEmpty()) {
            throw new ApiException("文档无段落数据，无法重新向量化");
        }

        // 1. 删除旧向量
        vectorStore.deleteByDocument(documentId);

        // 2. 批量重新嵌入（失败自动回退逐条容错）
        updateStatus(doc, DocumentStatus.EMBEDDING, "正在向量化");
        documentProcessingService.batchEmbedAndStore(doc, doc.getKnowledgeBaseId(), paragraphs);

        // 3. 更新文档状态
        long embeddedCount = paragraphs.stream()
                .filter(p -> "embedded".equals(p.getVectorStatus()))
                .count();
        doc.setParagraphCount(paragraphs.size());
        doc.setTokenCount(paragraphs.stream().mapToInt(p -> {
            if (p.getTokenCount() == null) return 0;
            return p.getTokenCount();
        }).sum());
        doc.setProcessMessage("向量化完成: " + embeddedCount + "/" + paragraphs.size());
        doc.setStatus(DocumentStatus.COMPLETED.getCode());
        updateById(doc);

        // 4. 更新知识库计数
        documentProcessingService.updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
        log.info("文档重新向量化完成: {} ({} 段落, {} 成功)", doc.getName(), paragraphs.size(), embeddedCount);
    }

    @Override
    public IPage<KnowledgeDocumentEntity> pageQuery(String knowledgeBaseId, Integer pageNum, Integer pageSize) {
        knowledgeBaseService.checkAccess(knowledgeBaseId);
        LambdaQueryWrapper<KnowledgeDocumentEntity> wrapper = new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocumentEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    /**
     * 更新文档状态与处理消息
     */
    private void updateStatus(KnowledgeDocumentEntity doc, DocumentStatus status, String message) {
        doc.setStatus(status.getCode());
        doc.setProcessMessage(message);
        updateById(doc);
    }

    private String md5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
