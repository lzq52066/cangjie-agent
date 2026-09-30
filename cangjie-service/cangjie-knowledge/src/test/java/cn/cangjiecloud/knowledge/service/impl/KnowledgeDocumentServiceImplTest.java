package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.enums.DocumentStatus;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeDocumentMapper;
import cn.cangjiecloud.knowledge.service.DocumentProcessingService;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import cn.cangjiecloud.oss.entity.FileEntity;
import cn.cangjiecloud.oss.service.IFileService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeDocumentServiceImpl} 单元测试。
 * <p>
 * 覆盖上传（含 MD5、事务提交后触发异步处理）、删除、重新处理、重新向量化与分页查询；
 * spy 拦截 MyBatis-Plus {@code ServiceImpl} 继承方法，不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {

    @Mock
    private IKnowledgeBaseService knowledgeBaseService;
    @Mock
    private IKnowledgeParagraphService paragraphService;
    @Mock
    private cn.cangjiecloud.core.rag.VectorStore vectorStore;
    @Mock
    private IFileService fileService;
    @Mock
    private DocumentProcessingService documentProcessingService;

    private KnowledgeDocumentServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(KnowledgeDocumentMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new KnowledgeDocumentServiceImpl(knowledgeBaseService, paragraphService,
                vectorStore, fileService, documentProcessingService));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------- 测试数据构造 ----------

    private static KnowledgeDocumentEntity document(String id, String status) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setId(id);
        doc.setKnowledgeBaseId("kb1");
        doc.setName("文档.md");
        doc.setTitle("文档标题");
        doc.setStatus(status);
        return doc;
    }

    private static KnowledgeParagraphEntity paragraph(String id, String vectorStatus, Integer tokens) {
        KnowledgeParagraphEntity p = new KnowledgeParagraphEntity();
        p.setId(id);
        p.setDocumentId("d1");
        p.setKnowledgeBaseId("kb1");
        p.setContent("段落内容-" + id);
        p.setVectorStatus(vectorStatus);
        p.setTokenCount(tokens);
        return p;
    }

    // ---------- upload ----------

    @Nested
    class Upload {

        @Test
        void shouldCreatePendingDocumentAndTriggerAsyncProcessAfterCommit() throws IOException {
            // 正常场景：生成 pending 文档、计算 MD5，事务提交后触发异步处理
            KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
            kb.setId("kb1");
            when(knowledgeBaseService.getById("kb1")).thenReturn(kb);
            FileEntity fileEntity = new FileEntity();
            fileEntity.setId("f1");
            when(fileService.upload(any(MultipartFile.class), eq("document"))).thenReturn(fileEntity);
            doAnswer(invocation -> {
                ((KnowledgeDocumentEntity) invocation.getArgument(0)).setId("d1");
                return true;
            }).when(service).save(any());
            TransactionSynchronizationManager.initSynchronization();
            byte[] bytes = "hello 知识库".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile("file", "测试文档.md",
                    "text/markdown", bytes);

            KnowledgeDocumentEntity doc = service.upload("kb1", file);

            verify(knowledgeBaseService).checkAccess("kb1");
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING.getCode());
            assertThat(doc.getFileType()).isEqualTo("markdown");
            assertThat(doc.getName()).isEqualTo("测试文档.md");
            assertThat(doc.getTitle()).isEqualTo("测试文档.md");
            assertThat(doc.getFileId()).isEqualTo("f1");
            assertThat(doc.getFileSize()).isEqualTo((long) bytes.length);
            assertThat(doc.getFileMd5()).isEqualTo(DigestUtils.md5DigestAsHex(bytes));

            // 手动触发事务提交后的回调，验证异步处理被调度
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(documentProcessingService).processAsync("d1", bytes);
        }

        @Test
        void shouldThrowWhenKnowledgeBaseMissing() {
            // 异常场景：知识库不存在时不保存文件
            when(knowledgeBaseService.getById("kb1")).thenReturn(null);
            MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown",
                    "x".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> service.upload("kb1", file))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("知识库不存在");
            verify(fileService, never()).upload(any(MultipartFile.class), anyString());
        }

        @Test
        void shouldWrapIOExceptionWhenReadingFileBytes() throws IOException {
            // 异常场景：读取文件字节失败包装为 ApiException
            KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
            kb.setId("kb1");
            when(knowledgeBaseService.getById("kb1")).thenReturn(kb);
            MultipartFile file = mock(MultipartFile.class);
            when(file.getBytes()).thenThrow(new IOException("磁盘错误"));

            assertThatThrownBy(() -> service.upload("kb1", file))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("读取文件失败: 磁盘错误");
        }
    }

    // ---------- delete ----------

    @Nested
    class Delete {

        @Test
        void shouldDoNothingWhenDocumentMissing() {
            // 边界场景：文档不存在时静默返回
            doReturn(null).when(service).getById("missing");

            service.delete("missing");

            verify(vectorStore, never()).deleteByDocument(anyString());
            verify(paragraphService, never()).deleteByDocument(anyString());
            verify(documentProcessingService, never()).updateKnowledgeBaseCount(anyString());
        }

        @Test
        void shouldRemoveVectorsParagraphsAndDocument() {
            // 正常场景：级联删除向量、段落、文档并刷新计数
            doReturn(document("d1", "completed")).when(service).getById("d1");
            doReturn(true).when(service).removeById(anyString());

            service.delete("d1");

            verify(knowledgeBaseService).checkAccess("kb1");
            verify(vectorStore).deleteByDocument("d1");
            verify(paragraphService).deleteByDocument("d1");
            verify(service).removeById("d1");
            verify(documentProcessingService).updateKnowledgeBaseCount("kb1");
        }
    }

    // ---------- reprocess / reEmbed ----------

    @Nested
    class Reprocess {

        @Test
        void shouldThrowWhenDocumentMissing() {
            // 异常场景：文档不存在
            doReturn(null).when(service).getById("missing");

            assertThatThrownBy(() -> service.reprocess("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文档不存在");
            assertThatThrownBy(() -> service.reEmbed("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文档不存在");
        }

        @Test
        void shouldRejectReprocessWhenStatusNotFailed() {
            // 异常场景：仅失败状态的文档允许重新处理
            doReturn(document("d1", DocumentStatus.COMPLETED.getCode())).when(service).getById("d1");

            assertThatThrownBy(() -> service.reprocess("d1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("仅失败状态的文档可重新处理");
            verify(service, never()).reEmbedDocument(any());
        }

        @Test
        void shouldReEmbedWhenReprocessFailedDocument() {
            // 正常场景：失败文档重新处理走重新向量化
            KnowledgeDocumentEntity doc = document("d1", DocumentStatus.FAILED.getCode());
            doReturn(doc).when(service).getById("d1");
            doNothing().when(service).reEmbedDocument(any());

            service.reprocess("d1");

            verify(knowledgeBaseService).checkAccess("kb1");
            verify(service).reEmbedDocument(doc);
        }

        @Test
        void shouldReEmbedRegardlessOfStatus() {
            // 正常场景：reEmbed 不限制文档状态
            KnowledgeDocumentEntity doc = document("d1", DocumentStatus.COMPLETED.getCode());
            doReturn(doc).when(service).getById("d1");
            doNothing().when(service).reEmbedDocument(any());

            service.reEmbed("d1");

            verify(service).reEmbedDocument(doc);
        }
    }

    // ---------- reEmbedDocument ----------

    @Nested
    class ReEmbedDocument {

        @Test
        void shouldThrowWhenNoParagraphs() {
            // 异常场景：文档无段落数据无法重新向量化
            KnowledgeDocumentEntity doc = document("d1", DocumentStatus.COMPLETED.getCode());
            when(paragraphService.listByDocument("d1")).thenReturn(List.of());

            assertThatThrownBy(() -> service.reEmbedDocument(doc))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文档无段落数据，无法重新向量化");
            verify(vectorStore, never()).deleteByDocument(anyString());
        }

        @Test
        void shouldDeleteOldVectorsAndUpdateDocumentStats() {
            // 正常场景：删除旧向量 → 重新嵌入 → 统计段落/token → 标记完成
            KnowledgeDocumentEntity doc = document("d1", DocumentStatus.COMPLETED.getCode());
            List<KnowledgeParagraphEntity> paragraphs = List.of(
                    paragraph("p1", "embedded", 10),
                    paragraph("p2", "pending", null));
            when(paragraphService.listByDocument("d1")).thenReturn(paragraphs);
            doReturn(true).when(service).updateById(any());

            service.reEmbedDocument(doc);

            verify(vectorStore).deleteByDocument("d1");
            verify(documentProcessingService).batchEmbedAndStore(doc, "kb1", paragraphs);
            // 内部 updateStatus(EMBEDDING) 与最终统计落库各调用一次
            verify(service, times(2)).updateById(doc);
            assertThat(doc.getParagraphCount()).isEqualTo(2);
            assertThat(doc.getTokenCount()).isEqualTo(10);
            assertThat(doc.getProcessMessage()).isEqualTo("向量化完成: 1/2");
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED.getCode());
            verify(documentProcessingService).updateKnowledgeBaseCount("kb1");
        }
    }

    // ---------- pageQuery ----------

    @Nested
    class PageQuery {

        @Test
        void shouldApplyPagingDefaults() {
            // 边界场景：分页参数缺省 1/10
            Page<KnowledgeDocumentEntity> page = new Page<>(1, 10);
            doReturn(page).when(service).page(any(), any());

            IPage<KnowledgeDocumentEntity> result = service.pageQuery("kb1", null, null);

            assertThat(result).isSameAs(page);
            verify(knowledgeBaseService).checkAccess("kb1");
        }

        @Test
        void shouldUseProvidedPagingParameters() {
            // 正常场景：显式分页参数透传给 Page
            Page<KnowledgeDocumentEntity> page = new Page<>(2, 5);
            doReturn(page).when(service).page(any(), any());

            service.pageQuery("kb1", 2, 5);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<KnowledgeDocumentEntity>> captor =
                    ArgumentCaptor.forClass(Page.class);
            verify(service).page(captor.capture(), any());
            assertThat(captor.getValue().getCurrent()).isEqualTo(2);
            assertThat(captor.getValue().getSize()).isEqualTo(5);
        }
    }
}
