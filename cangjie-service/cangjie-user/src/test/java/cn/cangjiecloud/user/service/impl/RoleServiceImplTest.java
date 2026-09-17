package cn.cangjiecloud.user.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.RoleMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.service.PermissionQueryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link RoleServiceImpl} 单元测试，spy 拦截所有 MyBatis-Plus DB 方法，不连接数据库。
 */
class RoleServiceImplTest {

    private UserRoleMapper userRoleMapper;
    private PermissionQueryService permissionQueryService;
    private RoleServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        initTableInfo(RoleMapper.class, RoleEntity.class);
        initTableInfo(UserRoleMapper.class, UserRoleEntity.class);
    }

    /** 让 LambdaQueryWrapper 能把 SFunction 解析成列名，无需真实数据库；namespace 只能设置一次，故每个实体各用一个 assistant */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(UserRoleMapper.class);
        permissionQueryService = mock(PermissionQueryService.class);
        service = spy(new RoleServiceImpl(userRoleMapper, permissionQueryService));
    }

    private static RoleDTO roleDTO(String name, String code, String desc, String status) {
        RoleDTO dto = new RoleDTO();
        dto.setName(name);
        dto.setCode(code);
        dto.setDescription(desc);
        dto.setStatus(status);
        return dto;
    }

    private static RoleEntity role(String id, String name) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCode("CODE");
        entity.setStatus("active");
        return entity;
    }

    private static UserRoleEntity userRole(String userId, String roleId) {
        UserRoleEntity ur = new UserRoleEntity();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        return ur;
    }

    // ---------- create ----------

    @Test
    void createShouldFillDefaultStatusWhenBlank() {
        doReturn(true).when(service).save(any(RoleEntity.class));

        RoleEntity saved = service.create(roleDTO("运维", "OPS", "描述", "  "));

        ArgumentCaptor<RoleEntity> cap = ArgumentCaptor.forClass(RoleEntity.class);
        verify(service).save(cap.capture());
        assertThat(saved).isSameAs(cap.getValue());
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getName()).isEqualTo("运维");
        assertThat(saved.getCode()).isEqualTo("OPS");
        assertThat(saved.getDescription()).isEqualTo("描述");
    }

    @Test
    void createShouldKeepGivenStatusAndTolerateNullFields() {
        doReturn(true).when(service).save(any(RoleEntity.class));

        RoleEntity saved = service.create(roleDTO(null, null, null, "inactive"));

        assertThat(saved.getStatus()).isEqualTo("inactive");
        assertThat(saved.getName()).isNull();
        assertThat(saved.getCode()).isNull();
        assertThat(saved.getDescription()).isNull();
    }

    // ---------- update ----------

    @Test
    void updateShouldThrowWhenRoleMissing() {
        doReturn(null).when(service).getById(any(Serializable.class));

        assertThatThrownBy(() -> service.update("r404", roleDTO("x", "y", "z", "active")))
                .isInstanceOf(ApiException.class)
                .hasMessage("角色不存在");
        verify(service, never()).updateById(any(RoleEntity.class));
    }

    @Test
    void updateShouldOnlyOverwriteNonBlankFields() {
        RoleEntity exists = role("r1", "旧名称");
        exists.setDescription("旧描述");
        doReturn(exists).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).updateById(any(RoleEntity.class));

        RoleDTO dto = roleDTO(null, "", "  ", "inactive");
        dto.setName("新名称");

        RoleEntity result = service.update("r1", dto);

        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getCode()).isEqualTo("CODE");
        assertThat(result.getDescription()).isEqualTo("旧描述");
        assertThat(result.getStatus()).isEqualTo("inactive");
        verify(service).updateById(exists);
    }

    @Test
    void updateShouldKeepEverythingWhenDtoIsEmpty() {
        RoleEntity exists = role("r2", "保持");
        doReturn(exists).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).updateById(any(RoleEntity.class));

        RoleEntity result = service.update("r2", roleDTO(null, null, null, null));

        assertThat(result.getName()).isEqualTo("保持");
        assertThat(result.getStatus()).isEqualTo("active");
        verify(service).updateById(exists);
    }

    // ---------- delete ----------

    @Test
    void deleteShouldThrowWhenRoleMissing() {
        doReturn(null).when(service).getById(any(Serializable.class));

        assertThatThrownBy(() -> service.delete("r404"))
                .isInstanceOf(ApiException.class)
                .hasMessage("角色不存在");
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
        verify(service, never()).removeById(any(Serializable.class));
    }

    @Test
    void deleteShouldRemoveRelationsAndEvictCache() {
        doReturn(role("r3", "待删")).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).removeById(any(Serializable.class));

        service.delete("r3");

        ArgumentCaptor<Wrapper<UserRoleEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(userRoleMapper).delete(wrapperCap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).contains("role_id");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("r3");
        verify(service).removeById("r3");
        verify(permissionQueryService).evictAll();
    }

    // ---------- pageQuery ----------

    @Test
    void pageQueryShouldApplyKeywordAndStatusFilters() {
        IPage<RoleEntity> stub = new Page<>(1, 10);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        RoleQueryDTO query = new RoleQueryDTO();
        query.setKeyword("adm");
        query.setStatus("active");
        query.setPageNum(3);
        query.setPageSize(25);

        IPage<RoleEntity> result = service.pageQuery(query);
        assertThat(result).isSameAs(stub);

        ArgumentCaptor<IPage<RoleEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RoleEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());

        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(3);
        assertThat(requested.getSize()).isEqualTo(25);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        String sql = wrapper.getTargetSql();
        assertThat(sql).contains("name LIKE").contains("code LIKE").contains("status =");
        assertThat(sql).containsIgnoringCase("ORDER BY create_time DESC");
        assertThat(wrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("%adm%", "%adm%", "active");
    }

    @Test
    void pageQueryShouldUseDefaultPagingAndSkipBlankFilters() {
        IPage<RoleEntity> stub = new Page<>(1, 10);
        doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

        RoleQueryDTO query = new RoleQueryDTO();
        query.setKeyword(null);
        query.setStatus("");
        query.setPageNum(null);
        query.setPageSize(null);

        service.pageQuery(query);

        ArgumentCaptor<IPage<RoleEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RoleEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(pageCap.capture(), wrapperCap.capture());
        Page<?> requested = (Page<?>) pageCap.getValue();
        assertThat(requested.getCurrent()).isEqualTo(1);
        assertThat(requested.getSize()).isEqualTo(10);

        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).doesNotContain("LIKE").doesNotContain("status");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    // ---------- listRolesByUserId ----------

    @Test
    void listRolesByUserIdShouldReturnEmptyWhenNoRelation() {
        doReturn(List.of()).when(userRoleMapper).selectList(any(Wrapper.class));

        assertThat(service.listRolesByUserId("u1")).isEmpty();
        verify(service, never()).listByIds(anyCollection());
    }

    @Test
    void listRolesByUserIdShouldLoadRolesByCollectedIds() {
        doReturn(List.of(userRole("u1", "r1"), userRole("u1", "r2")))
                .when(userRoleMapper).selectList(any(Wrapper.class));
        doReturn(List.of(role("r1", "管理员"), role("r2", "审计")))
                .when(service).listByIds(anyCollection());

        List<RoleEntity> roles = service.listRolesByUserId("u1");

        assertThat(roles).hasSize(2);
        ArgumentCaptor<Collection<? extends Serializable>> cap = ArgumentCaptor.forClass(Collection.class);
        verify(service).listByIds(cap.capture());
        assertThat(cap.getValue()).extracting(Object::toString).containsExactly("r1", "r2");
    }

    // ---------- assignUsersToRole ----------

    @Test
    void assignUsersToRoleShouldThrowWhenRoleIdNull() {
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setUserIds(List.of("u1"));

        assertThatThrownBy(() -> service.assignUsersToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和用户列表");
    }

    @Test
    void assignUsersToRoleShouldThrowWhenUserIdsNull() {
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r1");

        assertThatThrownBy(() -> service.assignUsersToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和用户列表");
    }

    @Test
    void assignUsersToRoleShouldThrowWhenUserIdsEmpty() {
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r1");
        dto.setUserIds(List.of());

        assertThatThrownBy(() -> service.assignUsersToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和用户列表");
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void assignUsersToRoleShouldRebuildRelationsAndEvictCache() {
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r1");
        dto.setUserIds(List.of("u1", "u2"));

        service.assignUsersToRole(dto);

        ArgumentCaptor<Wrapper<UserRoleEntity>> deleteCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(userRoleMapper).delete(deleteCap.capture());
        assertThat(((LambdaQueryWrapper<?>) deleteCap.getValue()).getTargetSql()).contains("role_id");

        ArgumentCaptor<UserRoleEntity> insertCap = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleMapper, times(2)).insert(insertCap.capture());
        assertThat(insertCap.getAllValues()).extracting(UserRoleEntity::getRoleId)
                .containsExactly("r1", "r1");
        assertThat(insertCap.getAllValues()).extracting(UserRoleEntity::getUserId)
                .containsExactly("u1", "u2");
        verify(permissionQueryService).evictAll();
    }
}
