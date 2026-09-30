package cn.cangjiecloud.oss.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.oss.entity.FileEntity;
import cn.cangjiecloud.oss.service.IFileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileController} 单元测试：直接 new Controller 并 mock IFileService，
 * 覆盖上传透传、下载响应头分支（inline/attachment、默认元数据）与列表包装。
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private IFileService fileService;

    @Mock
    private MultipartFile multipartFile;

    private FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController(fileService);
    }

    private static FileEntity entity(String id, String name, String contentType) {
        FileEntity entity = new FileEntity();
        entity.setId(id);
        entity.setFileName(name);
        entity.setContentType(contentType);
        entity.setStatus("active");
        return entity;
    }

    private static String encoded(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Nested
    class UploadTest {

        @Test
        void uploadShouldReturnServiceResult() {
            FileEntity entity = entity("f1", "photo.png", "image/png");
            when(fileService.upload(multipartFile, "image")).thenReturn(entity);

            R<FileEntity> response = controller.upload(multipartFile, "image");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(entity);
            verify(fileService).upload(multipartFile, "image");
        }

        @Test
        void uploadShouldPropagateApiException() {
            when(fileService.upload(multipartFile, null))
                    .thenThrow(new ApiException("文件不能为空"));

            assertThatThrownBy(() -> controller.upload(multipartFile, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件不能为空");
        }
    }

    @Nested
    class DownloadTest {

        @Test
        void downloadShouldBuildInlineResponseWithEncodedFileName() {
            byte[] data = "image-bytes".getBytes(StandardCharsets.UTF_8);
            FileEntity entity = entity("f1", "照片 1.png", "image/png");
            when(fileService.getById("f1")).thenReturn(entity);
            when(fileService.download("f1")).thenReturn(data);

            ResponseEntity<byte[]> response = controller.download("f1", false);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(data);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
            String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(disposition).isEqualTo("inline; filename*=UTF-8''" + encoded("照片 1.png"));
            assertThat(disposition).contains("%20").doesNotContain("+");
        }

        @Test
        void downloadShouldBuildAttachmentDispositionWhenDownloadRequested() {
            FileEntity entity = entity("f1", "report.pdf", "application/pdf");
            when(fileService.getById("f1")).thenReturn(entity);
            when(fileService.download("f1")).thenReturn(new byte[]{1, 2});

            ResponseEntity<byte[]> response = controller.download("f1", true);

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("attachment; filename*=UTF-8''report.pdf");
        }

        @Test
        void downloadShouldFallBackToDefaultsWhenMetadataMissing() {
            byte[] data = new byte[]{3};
            when(fileService.getById("f9")).thenReturn(null);
            when(fileService.download("f9")).thenReturn(data);

            ResponseEntity<byte[]> response = controller.download("f9", false);

            assertThat(response.getBody()).isEqualTo(data);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("inline; filename*=UTF-8''file");
        }

        @Test
        void downloadShouldFallBackWhenEntityFieldsAreNull() {
            FileEntity entity = new FileEntity();
            entity.setId("f2");
            when(fileService.getById("f2")).thenReturn(entity);
            when(fileService.download("f2")).thenReturn(new byte[]{4});

            ResponseEntity<byte[]> response = controller.download("f2", true);

            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("attachment; filename*=UTF-8''file");
        }
    }

    @Nested
    class DeleteAndListTest {

        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("f1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(fileService).delete("f1");
        }

        @Test
        void listShouldWrapPagedRecordsIntoPageResult() {
            Page<FileEntity> page = new Page<>(2, 5);
            page.setRecords(List.of(entity("f1", "a.txt", "text/plain"), entity("f2", "b.txt", "text/plain")));
            page.setTotal(12);
            when(fileService.pageQuery("txt", "document", 2, 5)).thenReturn(page);

            R<PageResult<FileEntity>> response = controller.list("txt", "document", 2, 5);

            assertThat(response.isSuccess()).isTrue();
            PageResult<FileEntity> result = response.getData();
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(12);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(5);
        }
    }
}
