package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.oss.service.IFileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.rag.DocumentParserFactory;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import cn.cangjiecloud.core.rag.TextSplitterFactory;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.api.enums.DocumentStatus;
import cn.cangjiecloud.knowledge.api.enums.DocumentType;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.rag.CustomSeparatorTextSplitter;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeDocumentService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl
        extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocumentEntity>
        implements IKnowledgeDocumentService {

    private final IKnowledgeBaseService knowledgeBaseService;
    private final IKnowledgeParagraphService paragraphService;
    private final DocumentParserFactory parserFactory;
    private final TextSplitterFactory splitterFactory;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final IFileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentEntity upload(String knowledgeBaseId, MultipartFile file) {
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

        // 2. 异步处理（M1 同步执行，后续改为消息队列）
        try {
            processDocument(doc, kb, fileBytes);
        } catch (Exception e) {
            log.error("文档处理失败: {}", doc.getName(), e);
            doc.setStatus(DocumentStatus.FAILED.getCode());
            doc.setProcessMessage(e.getMessage());
            updateById(doc);
        }

        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String documentId) {
        KnowledgeDocumentEntity doc = getById(documentId);
        if (doc == null) return;

        // 删除向量
        vectorStore.deleteByDocument(documentId);
        // 删除段落
        paragraphService.deleteByDocument(documentId);
        // 删除文档
        removeById(documentId);

        // 更新知识库计数
        updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
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

        // 2. 重新嵌入
        updateStatus(doc, DocumentStatus.EMBEDDING, "正在向量化");
        for (KnowledgeParagraphEntity para : paragraphs) {
            try {
                float[] embedding = embeddingProvider.embed(para.getContent());
                vectorStore.store(para.getId(), embedding, para.getContent(),
                        java.util.Map.of("title", para.getTitle(), "documentId", documentId));
                para.setVectorStatus("embedded");
            } catch (Exception e) {
                log.warn("段落向量化失败: {} ({}), 跳过", para.getId(), e.getMessage());
                para.setVectorStatus("pending");
            }
        }
        paragraphService.updateBatchById(paragraphs);

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
        updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
        log.info("文档重新向量化完成: {} ({} 段落, {} 成功)", doc.getName(), paragraphs.size(), embeddedCount);
    }

    @Override
    public List<KnowledgeDocumentEntity> listByKnowledgeBase(String knowledgeBaseId) {
        return list(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocumentEntity::getCreateTime));
    }

    /**
     * 文档处理核心流程：解析 → 切片 → 嵌入 → 存储
     */
    private void processDocument(KnowledgeDocumentEntity doc, KnowledgeBaseEntity kb, byte[] fileBytes) {
        // 1. 解析
        updateStatus(doc, DocumentStatus.PARSING, "正在解析文档");
        String text = parserFactory.parse(new ByteArrayInputStream(fileBytes), doc.getName());
        log.info("文档解析完成: {} ({} 字符)", doc.getName(), text.length());

        // 2. 切片
        updateStatus(doc, DocumentStatus.SPLITTING, "正在切分文档");
        TextSplitter splitter = splitterFactory.get(kb.getSplitStrategy());
        int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : 500;
        
        List<TextChunk> chunks;
        if (splitter instanceof CustomSeparatorTextSplitter customSplitter) {
            // 自定义分段模式：解析 separators 字段
            List<String> separators = parseSeparators(kb.getSeparators());
            chunks = customSplitter.splitWithSeparators(text, chunkSize, 0, separators);
        } else {
            // 智能分段模式：忽略 overlap 参数
            chunks = splitter.split(text, chunkSize, 0);
        }
        log.info("文档切片完成: {} → {} 个段落", doc.getName(), chunks.size());

        // 3. 保存段落
        List<KnowledgeParagraphEntity> paragraphs = new ArrayList<>();
        List<VectorStore.VectorEntry> vectorEntries = new ArrayList<>();
        int totalTokens = 0;

        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            KnowledgeParagraphEntity para = new KnowledgeParagraphEntity();
            para.setKnowledgeBaseId(kb.getId());
            para.setDocumentId(doc.getId());
            para.setTitle(chunk.getSource() != null ? chunk.getSource() : doc.getTitle());
            para.setContent(chunk.getContent());
            para.setChunkIndex(i);
            para.setPageNumber(chunk.getPageNumber());
            para.setCharCount(chunk.getContent().length());
            para.setTokenCount(estimateTokens(chunk.getContent()));
            para.setVectorStatus("pending");
            paragraphs.add(para);
            totalTokens += para.getTokenCount();
        }

        // 批量保存段落
        paragraphService.saveBatch(paragraphs);

        // 4. 嵌入 + 存储向量
        updateStatus(doc, DocumentStatus.EMBEDDING, "正在向量化");
        for (KnowledgeParagraphEntity para : paragraphs) {
            float[] embedding = embeddingProvider.embed(para.getContent());
            vectorStore.store(para.getId(), embedding, para.getContent(),
                    java.util.Map.of("title", para.getTitle(), "documentId", doc.getId()));
            para.setVectorStatus("embedded");
        }
        paragraphService.updateBatchById(paragraphs);

        // 5. 更新文档状态
        doc.setStatus(DocumentStatus.COMPLETED.getCode());
        doc.setParagraphCount(paragraphs.size());
        doc.setTokenCount(totalTokens);
        doc.setProcessMessage("处理完成");
        updateById(doc);

        // 6. 更新知识库计数
        updateKnowledgeBaseCount(kb.getId());
        log.info("文档处理完成: {} ({} 段落, {} tokens)", doc.getName(), paragraphs.size(), totalTokens);
    }

    private void updateStatus(KnowledgeDocumentEntity doc, DocumentStatus status, String message) {
        doc.setStatus(status.getCode());
        doc.setProcessMessage(message);
        updateById(doc);
    }

    private void updateKnowledgeBaseCount(String knowledgeBaseId) {
        KnowledgeBaseEntity kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null) return;
        long docCount = count(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId));
        kb.setDocumentCount((int) docCount);
        // 段落计数
        long paraCount = paragraphService.listByKnowledgeBase(knowledgeBaseId).size();
        kb.setParagraphCount((int) paraCount);
        knowledgeBaseService.updateById(kb);
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        // 近似估算：中文 1 字 ≈ 1.5 token，英文 1 词 ≈ 1.3 token
        return (int) (text.length() * 0.75);
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

    /**
     * 解析分隔符 JSON 数组字符串
     */
    private List<String> parseSeparators(String separatorsJson) {
        if (separatorsJson == null || separatorsJson.isBlank()) {
            return List.of("blank_line");
        }
        try {
            // 简单解析 JSON 数组，如 ["h2","blank_line"]
            return java.util.Arrays.stream(separatorsJson.replaceAll("[\\[\\]\"]", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("分隔符解析失败，使用默认值: {}", separatorsJson);
            return List.of("blank_line");
        }
    }
}
