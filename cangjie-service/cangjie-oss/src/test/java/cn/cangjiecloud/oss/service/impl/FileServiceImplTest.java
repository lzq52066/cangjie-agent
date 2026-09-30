package cn.cangjiecloud.oss.service.impl;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.oss.entity.FileEntity;
import cn.cangjiecloud.oss.mapper.FileMapper;
import cn.cangjiecloud.oss.storage.FileStorage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FileServiceImpl} 单元测试。
 * <p>
 * spy 拦截继承自 MyBatis-Plus {@code ServiceImpl} 的 {@code save/updateById/getById/page}，
 * UserContext 用静态 mock 隔离，FileStorage 与 MultipartFile 使用 mock，不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileStorage fileStorage;

    @Mock
    private MultipartFile file;

    private FileServiceImpl service;
    private MockedStatic<UserContext> userContext;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能把 SFunction 解析成列名，无需真实数据库
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(FileMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, FileEntity.class);
    }

    @BeforeEach
    void setUp() {
        userContext = mockStatic(UserContext.class);
        service = spy(new FileServiceImpl(fileStorage));
    }

    @AfterEach
    void tearDown() {
        userContext.close();
    }

    // ---------- 测试数据与工具 ----------

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** 打桩 MultipartFile，仅覆盖 upload 正常路径使用到的方法 */
    private void stubFile(byte[] data, String name, String contentType) throws IOException {
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(data);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) data.length);
    }

    /** spy 的 save 会走 baseMapper（为 null），需拦截并模拟主键回填 */
    private void stubSaveAssignId(String id) {
        doAnswer(invocation -> {
            invocation.<FileEntity>getArgument(0).setId(id);
            return true;
        }).when(service).save(any(FileEntity.class));
        doReturn(true).when(service).updateById(any(FileEntity.class));
    }

    private static String md5Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(data)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Nested
    class UploadTest {

        @Test
        void uploadShouldRejectNullFile() {
            assertThatThrownBy(() -> service.upload(null, "image"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件不能为空");
            verifyNoInteractions(fileStorage);
        }

        @Test
        void uploadShouldRejectEmptyFile() {
            when(file.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> service.upload(file, "image"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件不能为空");
            verifyNoInteractions(fileStorage);
        }

        @Test
        void uploadShouldPersistEntityAndUseLocalDownloadUrlFallback() throws Exception {
            byte[] data = bytes("hello cangjie");
            stubFile(data, "photo.png", "image/png");
            when(fileStorage.upload(data, "photo.png", "image/png")).thenReturn("2026/09/30/abc.png");
            when(fileStorage.getType()).thenReturn("local");
            when(fileStorage.getUrl("2026/09/30/abc.png")).thenReturn(null);
            userContext.when(UserContext::getUserId).thenReturn("u1");
            stubSaveAssignId("f1");

            FileEntity entity = service.upload(file, "image");

            assertThat(entity.getId()).isEqualTo("f1");
            assertThat(entity.getFileName()).isEqualTo("photo.png");
            assertThat(entity.getStorageType()).isEqualTo("local");
            assertThat(entity.getFilePath()).isEqualTo("2026/09/30/abc.png");
            assertThat(entity.getFileSize()).isEqualTo(data.length);
            assertThat(entity.getContentType()).isEqualTo("image/png");
            assertThat(entity.getMd5()).isEqualTo(md5Hex(data));
            assertThat(entity.getCategory()).isEqualTo("image");
            assertThat(entity.getUploaderId()).isEqualTo("u1");
            assertThat(entity.getStatus()).isEqualTo("active");
            assertThat(entity.getUrl()).isEqualTo(AppConst.ADMIN_API + "/file/f1");
            verify(service).save(entity);
            verify(service).updateById(entity);
        }

        @Test
        void uploadShouldPreferPublicUrlFromObjectStorage() throws Exception {
            byte[] data = bytes("minio data");
            stubFile(data, "a.txt", "text/plain");
            when(fileStorage.upload(data, "a.txt", "text/plain")).thenReturn("2026/09/30/x.txt");
            when(fileStorage.getType()).thenReturn("minio");
            when(fileStorage.getUrl("2026/09/30/x.txt")).thenReturn("https://cdn.example.com/x.txt");
            stubSaveAssignId("f2");

            FileEntity entity = service.upload(file, "document");

            assertThat(entity.getUrl()).isEqualTo("https://cdn.example.com/x.txt");
        }

        @Test
        void uploadShouldDefaultCategoryToOtherWhenBlank() throws Exception {
            byte[] data = bytes("no category");
            stubFile(data, "a.bin", "application/octet-stream");
            when(fileStorage.upload(data, "a.bin", "application/octet-stream")).thenReturn("p");
            when(fileStorage.getType()).thenReturn("local");
            when(fileStorage.getUrl("p")).thenReturn(null);
            stubSaveAssignId("f3");

            FileEntity entity = service.upload(file, "   ");

            assertThat(entity.getCategory()).isEqualTo("other");
        }

        @Test
        void uploadShouldWrapIoExceptionFromGetBytes() throws IOException {
            when(file.isEmpty()).thenReturn(false);
            when(file.getBytes()).thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> service.upload(file, "image"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("读取上传文件失败");
            verifyNoInteractions(fileStorage);
        }
    }

    @Nested
    class DownloadTest {

        @Test
        void downloadShouldRejectMissingRecord() {
            doReturn(null).when(service).getById("missing");

            assertThatThrownBy(() -> service.download("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件不存在");
            verifyNoInteractions(fileStorage);
        }

        @Test
        void downloadShouldRejectDeletedRecord() {
            FileEntity entity = new FileEntity();
            entity.setId("f1");
            entity.setStatus("deleted");
            doReturn(entity).when(service).getById("f1");

            assertThatThrownBy(() -> service.download("f1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件已被删除");
            verifyNoInteractions(fileStorage);
        }

        @Test
        void downloadShouldReturnStorageBytesForActiveRecord() {
            FileEntity entity = new FileEntity();
            entity.setId("f1");
            entity.setStatus("active");
            entity.setFilePath("2026/09/30/a.txt");
            doReturn(entity).when(service).getById("f1");
            byte[] data = bytes("file body");
            when(fileStorage.download("2026/09/30/a.txt")).thenReturn(data);

            assertThat(service.download("f1")).isEqualTo(data);
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void deleteShouldRejectMissingRecord() {
            doReturn(null).when(service).getById("missing");

            assertThatThrownBy(() -> service.delete("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("文件不存在");
            verifyNoInteractions(fileStorage);
        }

        @Test
        void deleteShouldMarkRecordDeletedAndRemoveStorageFile() {
            FileEntity entity = new FileEntity();
            entity.setId("f1");
            entity.setStatus("active");
            entity.setFilePath("2026/09/30/a.txt");
            doReturn(entity).when(service).getById("f1");
            doReturn(true).when(service).updateById(entity);

            service.delete("f1");

            assertThat(entity.getStatus()).isEqualTo("deleted");
            verify(fileStorage).delete("2026/09/30/a.txt");
            verify(service).updateById(entity);
        }
    }

    @Nested
    class PageQueryTest {

        @Test
        void pageQueryShouldApplyFiltersAndFallBackToDefaultPaging() {
            IPage<FileEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<FileEntity> result = service.pageQuery("合同", "image", null, null);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<FileEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<FileEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            Wrapper<FileEntity> wrapper = wrapperCap.getValue();
            String sql = wrapper.getTargetSql() + " | " + wrapper.getSqlSegment();
            assertThat(sql).contains("status");
            assertThat(sql).contains("file_name");
            assertThat(sql).contains("category");
            assertThat(sql).containsIgnoringCase("ORDER BY");
        }

        @Test
        void pageQueryShouldSkipBlankFiltersAndKeepRequestedPaging() {
            IPage<FileEntity> stub = new Page<>(3, 50);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery("  ", null, 3, 50);

            ArgumentCaptor<IPage<FileEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<FileEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(3);
            assertThat(requested.getSize()).isEqualTo(50);

            Wrapper<FileEntity> wrapper = wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("file_name");
            assertThat(wrapper.getSqlSegment()).doesNotContain("category");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }
    }
}
