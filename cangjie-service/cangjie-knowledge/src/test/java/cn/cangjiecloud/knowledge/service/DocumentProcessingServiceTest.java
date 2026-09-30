package cn.cangjiecloud.knowledge.service;

import cn.cangjiecloud.core.rag.DocumentParserFactory;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.core.rag.TextChunk;
import cn.cangjiecloud.core.rag.TextSplitter;
import cn.cangjiecloud.core.rag.TextSplitterFactory;
import cn.cangjiecloud.core.rag.VectorStore;
import cn.cangjiecloud.knowledge.api.enums.DocumentStatus;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.mapper.KnowledgeParagraphMapper;
import cn.cangjiecloud.knowledge.rag.CustomSeparatorTextSplitter;
import cn.cangjiecloud.knowledge.rag.DocumentSummaryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentProcessingService} 单元测试。
 * <p>
 * 覆盖异步处理入口门卫、解析/切片/向量化流水线、批量嵌入回退与失败标记，
 * 全部依赖以 Mock 替代，不连数据库、不发起真实调用。
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private IKnowledgeBaseService knowledgeBaseService;
    @Mock
    private IKnowledgeParagraphService paragraphService;
    @Mock
    private DocumentParserFactory parserFactory;
    @Mock
    private TextSplitterFactory splitterFactory;
    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private DocumentSummaryService documentSummaryService;
    @Mock
    private TextSplitter smartSplitter;
    @Mock
    private CustomSeparatorTextSplitter customSplitter;

    private DocumentProcessingService service;

    @BeforeAll
    static void installTableInfoCache() {
        MapperBuilderAssistant docAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        docAssistant.setCurrentNamespace(KnowledgeDocumentMapper.class.getName());
        TableInfoHelper.initTableInfo(docAssistant, KnowledgeDocumentEntity.class);

        MapperBuilderAssistant paraAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        paraAssistant.setCurrentNamespace(KnowledgeParagraphMapper.class.getName());
        TableInfoHelper.initTableInfo(paraAssistant, KnowledgeParagraphEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingService(documentMapper, knowledgeBaseService, paragraphService,
                parserFactory, splitterFactory, embeddingProvider, vectorStore, documentSummaryService);
    }

    private KnowledgeDocumentEntity document(String status) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setId("d1");
        doc.setKnowledgeBaseId("kb1");
        doc.setName("doc.txt");
        doc.setTitle("文档标题");
        doc.setStatus(status);
        return doc;
    }

    private KnowledgeBaseEntity knowledgeBase() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId("kb1");
        kb.setSplitStrategy("smart");
        return kb;
    }

    private static TextChunk chunk(String content) {
        return TextChunk.builder().content(content).chunkIndex(0).build();
    }

    @Nested
    class ProcessAsyncGuards {

        @Test
        void shouldSkipWhenDocumentMissing() {
            // 异常场景：待处理文档不存在，直接跳过
            when(documentMapper.selectById("d1")).thenReturn(null);

            service.processAsync("d1", "hello".getBytes());

            verifyNoInteractions(knowledgeBaseService);
            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
        }

        @Test
        void shouldSkipWhenStatusNotPending() {
            // 边界场景：文档状态非 pending（如已完成），不重复处理
            when(documentMapper.selectById("d1")).thenReturn(document("completed"));

            service.processAsync("d1", "hello".getBytes());

            verifyNoInteractions(knowledgeBaseService);
            verify(documentMapper, never()).updateById(any(KnowledgeDocumentEntity.class));
        }

        @Test
        void shouldMarkFailedWhenKnowledgeBaseMissing() {
            // 异常场景：知识库不存在，标记文档失败并保留提示
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            when(documentMapper.selectById("d1")).thenReturn(doc);
            when(knowledgeBaseService.getById("kb1")).thenReturn(null);

            service.processAsync("d1", "hello".getBytes());

            verify(documentMapper).updateById(doc);
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED.getCode());
            assertThat(doc.getProcessMessage()).isEqualTo("知识库不存在");
        }

        @Test
        void shouldMarkFailedWithTruncatedMessageWhenCauseTooLong() {
            // 边界场景：异常信息超过 500 字符时截断存储
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            when(documentMapper.selectById("d1")).thenReturn(doc);
            when(knowledgeBaseService.getById("kb1")).thenReturn(knowledgeBase());
            when(parserFactory.parse(any(InputStream.class), eq("doc.txt")))
                    .thenThrow(new IllegalStateException("x".repeat(600)));

            service.processAsync("d1", "hello".getBytes());

            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED.getCode());
            assertThat(doc.getProcessMessage()).hasSize(500);
        }

        @Test
        void shouldRunFullPipelineOnSuccess() {
            // 正常场景：异步入口走通完整流水线并落库计数
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            when(documentMapper.selectById("d1")).thenReturn(doc);
            when(knowledgeBaseService.getById("kb1")).thenReturn(knowledgeBase());
            when(parserFactory.parse(any(InputStream.class), eq("doc.txt"))).thenReturn("parsed text");
            when(splitterFactory.get("smart")).thenReturn(smartSplitter);
            when(smartSplitter.split("parsed text", 500, 0))
                    .thenReturn(List.of(chunk("段落一"), chunk("段落二内容")));
            when(embeddingProvider.embedBatch(List.of("段落一", "段落二内容")))
                    .thenReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));

            service.processAsync("d1", "hello".getBytes());

            // 段落保存：chunkSize 缺省 500、标题回退文档标题、chunkIndex 递增
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<KnowledgeParagraphEntity>> paraCaptor = ArgumentCaptor.forClass(List.class);
            verify(paragraphService).saveBatch(paraCaptor.capture());
            List<KnowledgeParagraphEntity> paragraphs = paraCaptor.getValue();
            assertThat(paragraphs).hasSize(2);
            assertThat(paragraphs.get(0).getTitle()).isEqualTo("文档标题");
            assertThat(paragraphs.get(0).getDocumentId()).isEqualTo("d1");
            assertThat(paragraphs.get(0).getKnowledgeBaseId()).isEqualTo("kb1");
            assertThat(paragraphs.get(0).getChunkIndex()).isZero();
            assertThat(paragraphs.get(1).getChunkIndex()).isEqualTo(1);
            assertThat(paragraphs.get(1).getContent()).isEqualTo("段落二内容");
            assertThat(paragraphs).allSatisfy(p -> assertThat(p.getVectorStatus()).isEqualTo("embedded"));

            // 文档状态流转至 completed，并写入段落数/Token 数
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED.getCode());
            assertThat(doc.getParagraphCount()).isEqualTo(2);
            assertThat(doc.getTokenCount()).isGreaterThan(0);
            assertThat(doc.getProcessMessage()).isEqualTo("处理完成");

            // 批量向量入库与摘要触发
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<VectorStore.VectorEntry>> entryCaptor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).storeBatch(entryCaptor.capture());
            assertThat(entryCaptor.getValue()).hasSize(2);
            verify(paragraphService).updateBatchById(paragraphs);
            verify(documentSummaryService).generateSummary("d1");
        }
    }

    @Nested
    class ProcessDocumentSplitting {

        @Test
        void shouldUseCustomSeparatorSplitterWithParsedSeparators() {
            // 正常场景：自定义分段策略走 splitWithSeparators 并解析分隔符 JSON
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            KnowledgeBaseEntity kb = knowledgeBase();
            kb.setSplitStrategy("custom");
            kb.setChunkSize(300);
            kb.setSeparators("[\"h1\", \"blank_line\"]");
            when(parserFactory.parse(any(InputStream.class), eq("doc.txt"))).thenReturn("正文");
            when(splitterFactory.get("custom")).thenReturn(customSplitter);
            when(customSplitter.splitWithSeparators("正文", 300, 0, List.of("h1", "blank_line")))
                    .thenReturn(List.of(chunk("正文")));
            when(embeddingProvider.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{0.1f}));

            service.processDocument(doc, kb, "hello".getBytes());

            verify(customSplitter).splitWithSeparators("正文", 300, 0, List.of("h1", "blank_line"));
            verify(smartSplitter, never()).split(anyString(), anyInt(), anyInt());
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED.getCode());
        }

        @Test
        void shouldFallbackToBlankLineWhenSeparatorsBlank() {
            // 边界场景：分隔符配置为空白时回退默认 blank_line
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            KnowledgeBaseEntity kb = knowledgeBase();
            kb.setSplitStrategy("custom");
            kb.setSeparators("   ");
            when(parserFactory.parse(any(InputStream.class), eq("doc.txt"))).thenReturn("正文");
            when(splitterFactory.get("custom")).thenReturn(customSplitter);
            when(customSplitter.splitWithSeparators("正文", 500, 0, List.of("blank_line")))
                    .thenReturn(List.of(chunk("正文")));
            when(embeddingProvider.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{0.1f}));

            service.processDocument(doc, kb, "hello".getBytes());

            verify(customSplitter).splitWithSeparators("正文", 500, 0, List.of("blank_line"));
        }

        @Test
        void shouldNotFailMainFlowWhenSummaryGenerationThrows() {
            // 异常场景：摘要生成异常不影响主流程完成
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            KnowledgeBaseEntity kb = knowledgeBase();
            when(parserFactory.parse(any(InputStream.class), eq("doc.txt"))).thenReturn("正文");
            when(splitterFactory.get("smart")).thenReturn(smartSplitter);
            when(smartSplitter.split("正文", 500, 0)).thenReturn(List.of(chunk("正文")));
            when(embeddingProvider.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{0.1f}));
            doThrow(new RuntimeException("摘要服务不可用"))
                    .when(documentSummaryService).generateSummary("d1");

            service.processDocument(doc, kb, "hello".getBytes());

            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED.getCode());
            verify(documentSummaryService).generateSummary("d1");
        }
    }

    @Nested
    class BatchEmbedAndStore {

        @Test
        void shouldEmbedInBatchAndMarkAllEmbedded() {
            // 正常场景：批量嵌入成功，一次性写入向量库
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            KnowledgeParagraphEntity p1 = new KnowledgeParagraphEntity();
            p1.setId("p1");
            p1.setContent("一");
            p1.setTitle("标题一");
            KnowledgeParagraphEntity p2 = new KnowledgeParagraphEntity();
            p2.setId("p2");
            p2.setContent("二");
            p2.setTitle("标题二");
            List<KnowledgeParagraphEntity> paragraphs = List.of(p1, p2);
            when(embeddingProvider.embedBatch(List.of("一", "二")))
                    .thenReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));

            service.batchEmbedAndStore(doc, "kb1", paragraphs);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<VectorStore.VectorEntry>> entryCaptor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).storeBatch(entryCaptor.capture());
            assertThat(entryCaptor.getValue()).hasSize(2);
            assertThat(entryCaptor.getValue().get(0).paragraphId()).isEqualTo("p1");
            assertThat(entryCaptor.getValue().get(1).documentId()).isEqualTo("d1");
            assertThat(paragraphs).allSatisfy(p -> assertThat(p.getVectorStatus()).isEqualTo("embedded"));
            verify(embeddingProvider, never()).embed(anyString());
            verify(paragraphService).updateBatchById(paragraphs);
        }

        @Test
        void shouldFallbackToPerItemEmbeddingWhenBatchFails() {
            // 异常场景：整批嵌入失败回退逐条，失败段落标记 pending
            KnowledgeDocumentEntity doc = document(DocumentStatus.PENDING.getCode());
            KnowledgeParagraphEntity ok = new KnowledgeParagraphEntity();
            ok.setId("p1");
            ok.setContent("一");
            ok.setTitle("标题一");
            KnowledgeParagraphEntity bad = new KnowledgeParagraphEntity();
            bad.setId("p2");
            bad.setContent("二");
            bad.setTitle("标题二");
            List<KnowledgeParagraphEntity> paragraphs = List.of(ok, bad);
            when(embeddingProvider.embedBatch(anyList())).thenThrow(new RuntimeException("批量接口超时"));
            when(embeddingProvider.embed("一")).thenReturn(new float[]{0.1f});
            when(embeddingProvider.embed("二")).thenThrow(new RuntimeException("单条失败"));

            service.batchEmbedAndStore(doc, "kb1", paragraphs);

            verify(vectorStore).store(eq("p1"), any(float[].class), eq("一"), any());
            assertThat(ok.getVectorStatus()).isEqualTo("embedded");
            assertThat(bad.getVectorStatus()).isEqualTo("pending");
            verify(vectorStore, never()).storeBatch(anyList());
            verify(paragraphService).updateBatchById(paragraphs);
        }
    }

    @Nested
    class UpdateKnowledgeBaseCount {

        @Test
        void shouldSkipUpdateWhenKnowledgeBaseMissing() {
            // 异常场景：知识库已删除时不更新计数
            when(knowledgeBaseService.getById("kb1")).thenReturn(null);

            service.updateKnowledgeBaseCount("kb1");

            verify(documentMapper, never()).selectCount(any());
            verify(knowledgeBaseService, never()).updateById(any());
        }

        @Test
        void shouldRefreshDocumentAndParagraphCounters() {
            // 正常场景：按 COUNT 聚合刷新文档数与段落数
            KnowledgeBaseEntity kb = knowledgeBase();
            when(knowledgeBaseService.getById("kb1")).thenReturn(kb);
            when(documentMapper.selectCount(any())).thenReturn(3L);
            when(paragraphService.count(any())).thenReturn(12L);

            service.updateKnowledgeBaseCount("kb1");

            assertThat(kb.getDocumentCount()).isEqualTo(3);
            assertThat(kb.getParagraphCount()).isEqualTo(12);
            verify(knowledgeBaseService).updateById(kb);
        }
    }
}
