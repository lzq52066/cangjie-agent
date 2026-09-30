package cn.cangjiecloud.knowledge.service.impl;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeBaseMapper;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link KnowledgeBaseServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截继承自 MyBatis-Plus {@code ServiceImpl} 的 save/getById/updateById/removeById/page，
 * 不连数据库；Sa-Token 上下文通过 {@link UserContext} 静态 mock 隔离。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    private KnowledgeBaseServiceImpl service;
    private MockedStatic<UserContext> userContextMock;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(KnowledgeBaseMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, KnowledgeBaseEntity.class);
    }

    @BeforeEach
    void setUp() {
        userContextMock = mockStatic(UserContext.class);
        service = spy(new KnowledgeBaseServiceImpl());
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ---------- 测试数据构造 ----------

    private static KnowledgeBaseCreateDTO createDTO() {
        KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
        dto.setName("库A");
        dto.setDescription("描述");
        dto.setSplitStrategy("custom");
        dto.setChunkSize(300);
        dto.setSeparators("[\"h2\"]");
        dto.setEmbeddingModelId("m1");
        dto.setDirectory("/a");
        return dto;
    }

    private static KnowledgeBaseEntity base(String id, String visibility, String createBy) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setName("库-" + id);
        entity.setVisibility(visibility);
        entity.setCreateBy(createBy);
        entity.setChunkSize(500);
        entity.setSplitStrategy("smart");
        entity.setStatus("active");
        return entity;
    }

    private void stubIdentity(String role) {
        userContextMock.when(UserContext::getIdentity).thenReturn(
                UserIdentity.builder().userId("me").role(role).build());
    }

    private void stubUserId(String userId) {
        userContextMock.when(UserContext::getUserId).thenReturn(userId);
    }

    // ---------- create ----------

    @Nested
    class Create {

        @Test
        void shouldApplyDefaultsAndNormalizeVisibility() {
            // 正常场景：chunkSize 缺省 500、可见性大写归一到小写、计数与状态初始化
            KnowledgeBaseCreateDTO dto = createDTO();
            dto.setChunkSize(null);
            dto.setVisibility("PUBLIC");
            doReturn(true).when(service).save(any());

            KnowledgeBaseEntity created = service.create(dto);

            assertThat(created.getVisibility()).isEqualTo("public");
            assertThat(created.getChunkSize()).isEqualTo(500);
            assertThat(created.getSplitStrategy()).isEqualTo("custom");
            assertThat(created.getDocumentCount()).isZero();
            assertThat(created.getParagraphCount()).isZero();
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getSeparators()).isEqualTo("[\"h2\"]");
            verify(service).save(created);
        }

        @Test
        void shouldFallbackToPrivateAndSmartForInvalidValues() {
            // 边界场景：非法可见性回退 private，非法切片策略回退 smart
            KnowledgeBaseCreateDTO dto = createDTO();
            dto.setVisibility("team");
            dto.setSplitStrategy(null);
            doReturn(true).when(service).save(any());

            KnowledgeBaseEntity created = service.create(dto);

            assertThat(created.getVisibility()).isEqualTo("private");
            assertThat(created.getSplitStrategy()).isEqualTo("smart");
        }
    }

    // ---------- update ----------

    @Nested
    class Update {

        @Test
        void shouldThrowWhenBaseNotFound() {
            // 异常场景：知识库不存在
            doReturn(null).when(service).getById("missing");

            assertThatThrownBy(() -> service.update("missing", new KnowledgeBaseUpdateDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("知识库不存在");
        }

        @Test
        void shouldRejectNonOwnerOnPrivateBase() {
            // 异常场景：私有库非创建者无权修改
            doReturn(base("kb1", "private", "u1")).when(service).getById("kb1");
            stubIdentity(AppConst.ROLE_USER);
            stubUserId("u2");

            assertThatThrownBy(() -> service.update("kb1", new KnowledgeBaseUpdateDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("无权访问该知识库");
            verify(service, never()).updateById(any());
        }

        @Test
        void shouldUpdateOnlyProvidedFields() {
            // 正常场景：仅更新非空字段，其余保持原值
            KnowledgeBaseEntity entity = base("kb1", "private", "u1");
            doReturn(entity).when(service).getById("kb1");
            doReturn(true).when(service).updateById(any());
            stubIdentity(AppConst.ROLE_USER);
            stubUserId("u1");

            KnowledgeBaseUpdateDTO dto = new KnowledgeBaseUpdateDTO();
            dto.setDescription("新描述");
            dto.setChunkSize(800);
            dto.setVisibility("public");

            KnowledgeBaseEntity updated = service.update("kb1", dto);

            assertThat(updated.getDescription()).isEqualTo("新描述");
            assertThat(updated.getChunkSize()).isEqualTo(800);
            assertThat(updated.getVisibility()).isEqualTo("public");
            // 未提供的字段保持原值
            assertThat(updated.getSplitStrategy()).isEqualTo("smart");
            assertThat(updated.getName()).isEqualTo("库-kb1");
            verify(service).updateById(entity);
        }

        @Test
        void shouldKeepOldVisibilityWhenNewValueInvalid() {
            // 边界场景：非法可见性回退为原可见性（公开库直接放行，无需查用户）
            KnowledgeBaseEntity entity = base("kb1", "public", "u1");
            doReturn(entity).when(service).getById("kb1");
            doReturn(true).when(service).updateById(any());
            stubIdentity(AppConst.ROLE_USER);

            KnowledgeBaseUpdateDTO dto = new KnowledgeBaseUpdateDTO();
            dto.setVisibility("unknown");

            assertThat(service.update("kb1", dto).getVisibility()).isEqualTo("public");
        }
    }

    // ---------- delete ----------

    @Nested
    class Delete {

        @Test
        void shouldSkipSilentlyWhenNotFound() {
            // 边界场景：库不存在时静默返回，不做删除
            doReturn(null).when(service).getById("missing");

            service.delete("missing");

            verify(service, never()).removeById(any(String.class));
        }

        @Test
        void shouldRemoveOwnedBase() {
            // 正常场景：创建者删除自己的库
            doReturn(base("kb1", "private", "u1")).when(service).getById("kb1");
            doReturn(true).when(service).removeById(any(String.class));
            stubIdentity(AppConst.ROLE_USER);
            stubUserId("u1");

            service.delete("kb1");

            verify(service).removeById("kb1");
        }
    }

    // ---------- pageQuery ----------

    @Nested
    class PageQuery {

        @Test
        void shouldSkipUserFilterForAdmin() {
            // 正常场景：管理员查询不做创建者过滤
            stubIdentity(AppConst.ROLE_ADMIN);
            Page<KnowledgeBaseEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(base("kb1", "private", "u1")));
            page.setTotal(1);
            doReturn(page).when(service).page(any(), any());

            IPage<KnowledgeBaseEntity> result = service.pageQuery(null, null, null);

            assertThat(result.getRecords()).hasSize(1);
            verify(service).page(any(), any());
        }

        @Test
        void shouldFilterByOwnerForNormalUserAndApplyDefaults() {
            // 正常场景：普通用户仅可见公开库与本人创建的库，分页参数缺省 1/10
            stubIdentity(AppConst.ROLE_USER);
            stubUserId("u1");
            Page<KnowledgeBaseEntity> page = new Page<>(1, 10);
            doReturn(page).when(service).page(any(), any());

            IPage<KnowledgeBaseEntity> result = service.pageQuery("关键词", null, null);

            assertThat(result).isSameAs(page);
            userContextMock.verify(UserContext::getUserId);
        }
    }

    // ---------- checkAccess ----------

    @Nested
    class CheckAccess {

        @Test
        void shouldPassForAdminWithoutLoadingEntity() {
            // 正常场景：管理员直接放行，不查库
            stubIdentity(AppConst.ROLE_ADMIN);

            assertThatCode(() -> service.checkAccess("kb1")).doesNotThrowAnyException();

            verify(service, never()).getById(any(String.class));
        }

        @Test
        void shouldPassForPublicBase() {
            // 正常场景：公开库任何人可访问（无需读取用户 ID）
            doReturn(base("kb1", "public", "u1")).when(service).getById("kb1");
            stubIdentity(AppConst.ROLE_USER);

            assertThatCode(() -> service.checkAccess("kb1")).doesNotThrowAnyException();
        }

        @Test
        void shouldRejectPrivateBaseForOtherUser() {
            // 异常场景：私有库非创建者访问被拒
            doReturn(base("kb1", "private", "u1")).when(service).getById("kb1");
            stubIdentity(AppConst.ROLE_USER);
            stubUserId("u2");

            assertThatThrownBy(() -> service.checkAccess("kb1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("无权访问该知识库");
        }

        @Test
        void shouldThrowWhenBaseMissing() {
            // 异常场景：知识库不存在
            doReturn(null).when(service).getById("missing");
            stubIdentity(AppConst.ROLE_USER);

            assertThatThrownBy(() -> service.checkAccess("missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("知识库不存在");
        }

        @Test
        void shouldRejectWhenUserIdIsNull() {
            // 边界场景：未登录用户（userId 为 null）访问私有库被拒
            doReturn(base("kb1", "private", "u1")).when(service).getById("kb1");
            stubIdentity(AppConst.ROLE_USER);

            assertThatThrownBy(() -> service.checkAccess("kb1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("无权访问该知识库");
        }
    }
}
