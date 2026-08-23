package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档摘要服务
 * <p>
 * 文档处理完成后，异步调用 LLM 生成文档级摘要并向量化存储。
 * 用于 two-stage 检索的第一步：从文档摘要中筛选候选文档，再进入段落级精准检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSummaryService {

    private final IModelService modelService;
    private final KnowledgeDocumentMapper documentMapper;
    private final IKnowledgeParagraphService paragraphService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;

    /**
     * 异步生成文档摘要
     */
    @Async
    public void generateSummary(String documentId) {
        KnowledgeDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("文档不存在，跳过摘要生成: {}", documentId);
            return;
        }

        try {
            List<KnowledgeParagraphEntity> paragraphs = paragraphService.listByDocument(documentId);
            if (paragraphs.isEmpty()) {
                log.warn("文档无段落数据，跳过摘要生成: {}", documentId);
                return;
            }

            // 拼接段落内容（取前 4000 字符作为摘要输入）
            String text = paragraphs.stream()
                    .map(KnowledgeParagraphEntity::getContent)
                    .collect(Collectors.joining("\n"));
            if (text.length() > 4000) {
                text = text.substring(0, 4000);
            }

            // 调用 LLM 生成摘要
            String summary = callLlmForSummary(doc, text);
            if (summary == null || summary.isBlank()) {
                log.warn("LLM 摘要生成返回空: {}", documentId);
                return;
            }

            // 存储摘要到文档记录
            doc.setSummary(summary);
            documentMapper.updateById(doc);

            // 向量化摘要并写入文档记录（用于文档级检索）
            float[] summaryEmbedding = embeddingProvider.embed(summary);
            vectorStore.storeDocumentSummary(documentId, summaryEmbedding, summary);

            log.info("文档摘要生成完成: {} ({} → {} 字符)", doc.getName(), text.length(), summary.length());
        } catch (Exception e) {
            log.error("文档摘要生成失败: {}", documentId, e.getMessage(), e);
        }
    }

    private String callLlmForSummary(KnowledgeDocumentEntity doc, String text) {
        try {
            OpenAICompatibleClient client = modelService.getDefaultClient();
            List<ChatMessage> messages = List.of(
                    ChatMessage.system("你是一个专业的文档摘要助手。请用 100-200 字概括以下文档的核心内容，只输出摘要本身，不要加任何前缀。"),
                    ChatMessage.user("请概括这篇文档的核心内容：\n\n" + text));
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0.3)
                    .maxTokens(300)
                    .build();
            ChatResponse response = client.chat(request);
            if (response != null && response.getContent() != null && !response.getContent().isBlank()) {
                return response.getContent().trim();
            }
        } catch (Exception e) {
            log.error("调用 LLM 生成摘要失败: {}", doc.getName(), e);
        }
        return null;
    }
}