package cn.cangjiecloud.knowledge.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeDocumentService;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeDocumentController} 单元测试。
 * <p>
 * 验证上传/删除/列表/详情/段落分页/重新向量化的委托与响应包装，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentControllerTest {

    @Mock
    private IKnowledgeDocumentService documentService;
    @Mock
    private IKnowledgeParagraphService paragraphService;

    private KnowledgeDocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeDocumentController(documentService, paragraphService);
    }

    private static KnowledgeDocumentEntity document(String id) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(id);
        entity.setName(id + ".txt");
        entity.setKnowledgeBaseId("kb1");
        return entity;
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "doc.txt", "text/plain", "hello".getBytes());
    }

    @Nested
    class UploadAndDelete {

        @Test
        void uploadShouldDelegateFileAndReturnEntity() {
            // 正常场景：上传文件透传 MultipartFile 并返回文档实体
            MockMultipartFile file = file();
            KnowledgeDocumentEntity uploaded = document("d1");
            when(documentService.upload("kb1", file)).thenReturn(uploaded);

            R<KnowledgeDocumentEntity> r = controller.upload("kb1", file);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isSameAs(uploaded);
            verify(documentService).upload("kb1", file);
        }

        @Test
        void uploadShouldPropagateServiceException() {
            // 异常场景：文件校验失败时异常向上抛出
            when(documentService.upload(eq("kb1"), any(MultipartFile.class)))
                    .thenThrow(new ApiException("不支持的文件格式"));

            assertThatThrownBy(() -> controller.upload("kb1", file()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("不支持");
        }

        @Test
        void deleteShouldReturnOk() {
            // 正常场景：删除返回成功响应
            R<Void> r = controller.delete("d1");

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isNull();
            verify(documentService).delete("d1");
        }

        @Test
        void reEmbedShouldReturnSuccessMessage() {
            // 正常场景：重新向量化返回带提示消息的成功响应
            R<Void> r = controller.reEmbed("d1");

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getMsg()).contains("重新向量化");
            verify(documentService).reEmbed("d1");
        }
    }

    @Nested
    class ReadOperations {

        @Test
        void getShouldReturnDocument() {
            // 正常场景：按 ID 查询文档详情
            KnowledgeDocumentEntity entity = document("d1");
            when(documentService.getById("d1")).thenReturn(entity);

            R<KnowledgeDocumentEntity> r = controller.get("d1");

            assertThat(r.getData()).isSameAs(entity);
        }

        @Test
        void listShouldMapPagedDocuments() {
            // 正常场景：文档分页映射为 PageResult
            Page<KnowledgeDocumentEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(document("d1"), document("d2")));
            page.setTotal(2L);
            when(documentService.pageQuery("kb1", 1, 10)).thenReturn(page);

            R<PageResult<KnowledgeDocumentEntity>> r = controller.list("kb1", 1, 10);

            assertThat(r.getData().getList()).hasSize(2);
            assertThat(r.getData().getTotal()).isEqualTo(2L);
            assertThat(r.getData().getPage()).isEqualTo(1L);
        }

        @Test
        void paragraphsShouldMapPagedParagraphs() {
            // 正常场景：段落分页经 paragraphService 映射为 PageResult
            KnowledgeParagraphEntity paragraph = new KnowledgeParagraphEntity();
            paragraph.setId("p1");
            paragraph.setDocumentId("d1");
            Page<KnowledgeParagraphEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(paragraph));
            page.setTotal(1L);
            when(paragraphService.pageQuery("d1", 1, 10)).thenReturn(page);

            R<PageResult<KnowledgeParagraphEntity>> r = controller.paragraphs("d1", 1, 10);

            assertThat(r.getData().getList()).hasSize(1);
            assertThat(r.getData().getList().get(0).getId()).isEqualTo("p1");
            verify(paragraphService).pageQuery("d1", 1, 10);
        }
    }
}
