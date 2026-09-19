package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.cangjiecloud.core.rag.DocumentParserFactory;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import cn.cangjiecloud.core.rag.TextSplitterFactory;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.core.model.TokenEstimator;
import cn.cangjiecloud.knowledge.api.enums.DocumentStatus;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.rag.CustomSeparatorTextSplitter;
import cn.cangjiecloud.knowledge.rag.DocumentSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档处理核心服务：解析 → 切片 → 批量向量化 → 入库
 * <p>
 * 独立为 Bean 以支持 {@code @Async} 代理（避免同类自调用失效），
 * 通过 Mapper 直接更新文档状态，避免与 {@link IKnowledgeDocumentService} 循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final KnowledgeDocumentMapper documentMapper;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final IKnowledgeParagraphService paragraphService;
    private final DocumentParserFactory parserFactory;
    private final TextSplitterFactory splitterFactory;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final DocumentSummaryService documentSummaryService;

    /**
     * 异步处理入口：上传事务提交后触发，失败标记文档为 failed（可通过重新处理恢复）
     */
    @Async("businessExecutor")
    public void processAsync(String documentId, byte[] fileBytes) {
        KnowledgeDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("待处理文档不存在: {}", documentId);
            return;
        }
        if (!DocumentStatus.PENDING.getCode().equals(doc.getStatus())) {
            log.warn("文档状态非待处理，跳过: {} ({})", doc.getName(), doc.getStatus());
            return;
        }
        KnowledgeBaseEntity kb = knowledgeBaseService.getById(doc.getKnowledgeBaseId());
        if (kb == null) {
            markFailed(doc, "知识库不存在");
            return;
        }
        try {
            processDocument(doc, kb, fileBytes);
        } catch (Exception e) {
            log.error("文档处理失败: {}", doc.getName(), e);
            markFailed(doc, e.getMessage());
        }
    }

    /**
     * 文档处理核心流程：解析 → 切片 → 批量嵌入 → 批量存储
     */
    public void processDocument(KnowledgeDocumentEntity doc, KnowledgeBaseEntity kb, byte[] fileBytes) {
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
            List<String> separators = parseSeparators(kb.getSeparators());
            chunks = customSplitter.splitWithSeparators(text, chunkSize, 0, separators);
        } else {
            chunks = splitter.split(text, chunkSize, 0);
        }
        log.info("文档切片完成: {} → {} 个段落", doc.getName(), chunks.size());

        // 3. 保存段落
        List<KnowledgeParagraphEntity> paragraphs = new ArrayList<>();
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
        paragraphService.saveBatch(paragraphs);

        // 4. 批量嵌入 + 批量存储向量
        updateStatus(doc, DocumentStatus.EMBEDDING, "正在向量化");
        batchEmbedAndStore(doc, kb.getId(), paragraphs);

        // 5. 更新文档状态
        doc.setStatus(DocumentStatus.COMPLETED.getCode());
        doc.setParagraphCount(paragraphs.size());
        doc.setTokenCount(totalTokens);
        doc.setProcessMessage("处理完成");
        documentMapper.updateById(doc);

        // 6. 更新知识库计数
        updateKnowledgeBaseCount(kb.getId());
        log.info("文档处理完成: {} ({} 段落, {} tokens)", doc.getName(), paragraphs.size(), totalTokens);

        // 7. 生成文档摘要（用于 two-stage 检索，@Async 内部执行）
        try {
            documentSummaryService.generateSummary(doc.getId());
        } catch (Exception e) {
            log.warn("触发文档摘要生成失败（不影响主流程）: {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * 批量向量化并写入：整批失败时回退为逐条容错模式（失败段落标记 pending）
     */
    public void batchEmbedAndStore(KnowledgeDocumentEntity doc, String knowledgeBaseId,
                                   List<KnowledgeParagraphEntity> paragraphs) {
        List<String> contents = paragraphs.stream()
                .map(KnowledgeParagraphEntity::getContent)
                .toList();
        try {
            List<float[]> embeddings = embeddingProvider.embedBatch(contents);
            List<VectorStore.VectorEntry> entries = new ArrayList<>(paragraphs.size());
            for (int i = 0; i < paragraphs.size(); i++) {
                KnowledgeParagraphEntity para = paragraphs.get(i);
                entries.add(new VectorStore.VectorEntry(para.getId(), embeddings.get(i),
                        para.getContent(), knowledgeBaseId, doc.getId(),
                        Map.of("title", para.getTitle(), "documentId", doc.getId())));
                para.setVectorStatus("embedded");
            }
            vectorStore.storeBatch(entries);
        } catch (Exception e) {
            log.warn("批量向量化失败，回退为逐条模式: {}", e.getMessage());
            for (KnowledgeParagraphEntity para : paragraphs) {
                try {
                    float[] embedding = embeddingProvider.embed(para.getContent());
                    vectorStore.store(para.getId(), embedding, para.getContent(),
                            Map.of("title", para.getTitle(), "documentId", doc.getId()));
                    para.setVectorStatus("embedded");
                } catch (Exception ex) {
                    log.warn("段落向量化失败: {} ({}), 跳过", para.getId(), ex.getMessage());
                    para.setVectorStatus("pending");
                }
            }
        }
        paragraphService.updateBatchById(paragraphs);
    }

    /**
     * 更新知识库文档/段落计数（COUNT 聚合，避免全量加载）
     */
    public void updateKnowledgeBaseCount(String knowledgeBaseId) {
        KnowledgeBaseEntity kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null) {
            return;
        }
        long docCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId));
        long paraCount = paragraphService.count(new LambdaQueryWrapper<KnowledgeParagraphEntity>()
                .eq(KnowledgeParagraphEntity::getKnowledgeBaseId, knowledgeBaseId));
        kb.setDocumentCount((int) docCount);
        kb.setParagraphCount((int) paraCount);
        knowledgeBaseService.updateById(kb);
    }

    private void markFailed(KnowledgeDocumentEntity doc, String message) {
        doc.setStatus(DocumentStatus.FAILED.getCode());
        doc.setProcessMessage(message != null && message.length() > 500
                ? message.substring(0, 500) : message);
        documentMapper.updateById(doc);
    }

    private void updateStatus(KnowledgeDocumentEntity doc, DocumentStatus status, String message) {
        doc.setStatus(status.getCode());
        doc.setProcessMessage(message);
        documentMapper.updateById(doc);
    }

    private int estimateTokens(String text) {
        return TokenEstimator.count(text);
    }

    private List<String> parseSeparators(String separatorsJson) {
        if (separatorsJson == null || separatorsJson.isBlank()) {
            return List.of("blank_line");
        }
        try {
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
