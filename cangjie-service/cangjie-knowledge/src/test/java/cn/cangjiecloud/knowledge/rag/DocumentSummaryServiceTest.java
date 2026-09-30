package cn.cangjiecloud.knowledge.rag;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentSummaryService} 单元测试。
 * <p>
 * 覆盖：文档/段落前置校验、成功路径（摘要落库 + 向量化存储）、4000 字符截断、
 * LLM 返回空白与各类异常的静默降级。
 */
@ExtendWith(MockitoExtension.class)
class DocumentSummaryServiceTest {

    @Mock
    private IModelService modelService;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private IKnowledgeParagraphService paragraphService;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private OpenAICompatibleClient client;

    private DocumentSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DocumentSummaryService(modelService, documentMapper, paragraphService,
                embeddingProvider, vectorStore);
    }

    private static KnowledgeDocumentEntity document(String id, String name) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setId(id);
        doc.setName(name);
        return doc;
    }

    private static KnowledgeParagraphEntity paragraph(String content) {
        KnowledgeParagraphEntity paragraph = new KnowledgeParagraphEntity();
        paragraph.setContent(content);
        return paragraph;
    }

    private KnowledgeDocumentEntity stubDocumentWithParagraphs(String docId, List<KnowledgeParagraphEntity> paragraphs) {
        KnowledgeDocumentEntity doc = document(docId, "文档A");
        when(documentMapper.selectById(docId)).thenReturn(doc);
        when(paragraphService.listByDocument(docId)).thenReturn(paragraphs);
        return doc;
    }

    private ArgumentCaptor<ChatRequest> stubLlmReturns(String content) {
        when(modelService.getDefaultClient()).thenReturn(client);
        when(client.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content(content).build());
        return ArgumentCaptor.forClass(ChatRequest.class);
    }

    @Nested
    class 前置校验 {

        @Test
        void 文档不存在时应跳过且不触碰其他依赖() {
            when(documentMapper.selectById("d-missing")).thenReturn(null);

            service.generateSummary("d-missing");

            verifyNoInteractions(paragraphService, modelService, embeddingProvider, vectorStore);
            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
        }

        @Test
        void 文档无段落时应跳过摘要生成() {
            when(documentMapper.selectById("d1")).thenReturn(document("d1", "空文档"));
            when(paragraphService.listByDocument("d1")).thenReturn(List.of());

            service.generateSummary("d1");

            verifyNoInteractions(modelService, embeddingProvider, vectorStore);
            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
        }
    }

    @Nested
    class 成功路径 {

        @Test
        void 成功路径应更新摘要并向量化存储() {
            KnowledgeDocumentEntity doc = stubDocumentWithParagraphs("d1",
                    List.of(paragraph("段落一"), paragraph("段落二")));
            ArgumentCaptor<ChatRequest> captor = stubLlmReturns(" 核心摘要 ");
            float[] embedding = {0.5f, 0.6f};
            when(embeddingProvider.embed("核心摘要")).thenReturn(embedding);

            service.generateSummary("d1");

            assertThat(doc.getSummary()).isEqualTo("核心摘要");
            verify(documentMapper).updateById(doc);
            verify(vectorStore).storeDocumentSummary("d1", embedding, "核心摘要");

            verify(client).chat(captor.capture());
            ChatRequest request = captor.getValue();
            assertThat(request.getTemperature()).isEqualTo(0.3);
            assertThat(request.getMaxTokens()).isEqualTo(300);
            assertThat(request.getMessages()).hasSize(2);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
            assertThat(request.getMessages().get(1).getContent()).contains("段落一\n段落二");
        }

        @Test
        void 段落拼接超过4000字符时应截断后送模型() {
            String partA = "A".repeat(3000);
            String partB = "B".repeat(3000);
            stubDocumentWithParagraphs("d1", List.of(paragraph(partA), paragraph(partB)));
            ArgumentCaptor<ChatRequest> captor = stubLlmReturns("摘要");
            when(embeddingProvider.embed("摘要")).thenReturn(new float[]{1f});

            service.generateSummary("d1");

            verify(client).chat(captor.capture());
            String userContent = captor.getValue().getMessages().get(1).getContent();
            assertThat(userContent).endsWith(partA + "\n" + "B".repeat(999));
            assertThat(userContent).doesNotContain("B".repeat(1000));
        }
    }

    @Nested
    class 异常与降级 {

        @Test
        void LLM返回空白摘要时应跳过存储() {
            stubDocumentWithParagraphs("d1", List.of(paragraph("段落")));
            stubLlmReturns("   ");

            service.generateSummary("d1");

            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
            verifyNoInteractions(embeddingProvider, vectorStore);
        }

        @Test
        void LLM调用异常应被吞掉() {
            stubDocumentWithParagraphs("d1", List.of(paragraph("段落")));
            when(modelService.getDefaultClient()).thenThrow(new RuntimeException("模型服务不可用"));

            assertThatCode(() -> service.generateSummary("d1")).doesNotThrowAnyException();

            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
            verifyNoInteractions(embeddingProvider, vectorStore);
        }

        @Test
        void 段落查询异常应被吞掉() {
            KnowledgeDocumentEntity doc = document("d1", "文档A");
            when(documentMapper.selectById("d1")).thenReturn(doc);
            when(paragraphService.listByDocument("d1")).thenThrow(new RuntimeException("DB 异常"));

            assertThatCode(() -> service.generateSummary("d1")).doesNotThrowAnyException();

            verifyNoInteractions(modelService, embeddingProvider, vectorStore);
            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
        }

        @Test
        void 向量化失败时摘要已落库且异常被吞掉() {
            KnowledgeDocumentEntity doc = stubDocumentWithParagraphs("d1", List.of(paragraph("段落")));
            stubLlmReturns("摘要");
            when(embeddingProvider.embed("摘要")).thenThrow(new RuntimeException("Embedding 服务不可用"));

            assertThatCode(() -> service.generateSummary("d1")).doesNotThrowAnyException();

            assertThat(doc.getSummary()).isEqualTo("摘要");
            verify(documentMapper).updateById(doc);
            verify(vectorStore, never()).storeDocumentSummary(any(), any(), any());
        }
    }
}
