package cn.cangjiecloud.user.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.service.PermissionQueryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
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
 * {@link MenuServiceImpl} 单元测试，spy 拦截 MyBatis-Plus DB 方法，纯内存运行。
 */
class MenuServiceImplTest {

    private RoleMenuMapper roleMenuMapper;
    private PermissionQueryService permissionQueryService;
    private MenuServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        initTableInfo(MenuMapper.class, MenuEntity.class);
        initTableInfo(RoleMenuMapper.class, RoleMenuEntity.class);
    }

    /** 让 LambdaQueryWrapper 能把 SFunction 解析成列名，无需真实数据库；namespace 只能设置一次，故每个实体各用一个 assistant */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        roleMenuMapper = mock(RoleMenuMapper.class);
        permissionQueryService = mock(PermissionQueryService.class);
        service = spy(new MenuServiceImpl(roleMenuMapper, permissionQueryService));
    }

    private static MenuDTO menuDTO() {
        MenuDTO dto = new MenuDTO();
        dto.setName("用户中心");
        dto.setCode("system:user");
        dto.setPath("/user");
        dto.setComponent("user/index");
        dto.setIcon("user");
        dto.setParentId("0");
        return dto;
    }

    private static MenuEntity menu(String id, String name, String parentId, Integer sort) {
        MenuEntity m = new MenuEntity();
        m.setId(id);
        m.setName(name);
        m.setParentId(parentId);
        m.setSort(sort);
        m.setType("menu");
        return m;
    }

    private static RoleMenuEntity roleMenu(String roleId, String menuId) {
        RoleMenuEntity rm = new RoleMenuEntity();
        rm.setRoleId(roleId);
        rm.setMenuId(menuId);
        return rm;
    }

    // ---------- create ----------

    @Test
    void createShouldApplyDefaultsWhenStatusSortTypeMissing() {
        doReturn(true).when(service).save(any(MenuEntity.class));
        MenuDTO dto = menuDTO();

        MenuEntity saved = service.create(dto);

        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getSort()).isZero();
        assertThat(saved.getType()).isEqualTo("menu");
        assertThat(saved.getName()).isEqualTo("用户中心");
        assertThat(saved.getCode()).isEqualTo("system:user");
        assertThat(saved.getPath()).isEqualTo("/user");
        assertThat(saved.getComponent()).isEqualTo("user/index");
        assertThat(saved.getIcon()).isEqualTo("user");
        assertThat(saved.getParentId()).isEqualTo("0");
        verify(service).save(saved);
    }

    @Test
    void createShouldKeepExplicitStatusSortType() {
        doReturn(true).when(service).save(any(MenuEntity.class));
        MenuDTO dto = new MenuDTO();
        dto.setStatus("inactive");
        dto.setSort(9);
        dto.setType("button");

        MenuEntity saved = service.create(dto);

        assertThat(saved.getStatus()).isEqualTo("inactive");
        assertThat(saved.getSort()).isEqualTo(9);
        assertThat(saved.getType()).isEqualTo("button");
    }

    // ---------- update ----------

    @Test
    void updateShouldThrowWhenMenuMissing() {
        doReturn(null).when(service).getById(any(Serializable.class));

        assertThatThrownBy(() -> service.update("m404", menuDTO()))
                .isInstanceOf(ApiException.class)
                .hasMessage("菜单不存在");
        verify(service, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void updateShouldOnlyOverwriteProvidedFields() {
        MenuEntity exists = menu("m1", "旧名称", "old-parent", 5);
        exists.setPath("/old");
        exists.setComponent("old/index");
        exists.setIcon("old");
        exists.setType("menu");
        exists.setStatus("active");
        exists.setCode("old:code");
        doReturn(exists).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).updateById(any(MenuEntity.class));

        MenuDTO dto = new MenuDTO();
        dto.setName("新名称");
        dto.setPath("/new");
        dto.setSort(1);

        MenuEntity result = service.update("m1", dto);

        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getPath()).isEqualTo("/new");
        assertThat(result.getSort()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("old:code");
        assertThat(result.getComponent()).isEqualTo("old/index");
        assertThat(result.getIcon()).isEqualTo("old");
        assertThat(result.getParentId()).isEqualTo("old-parent");
        assertThat(result.getType()).isEqualTo("menu");
        assertThat(result.getStatus()).isEqualTo("active");
        verify(service).updateById(exists);
    }

    @Test
    void updateShouldIgnoreNullSortAndBlankTexts() {
        MenuEntity exists = menu("m2", "名称", null, 7);
        doReturn(exists).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).updateById(any(MenuEntity.class));

        MenuDTO dto = new MenuDTO();
        dto.setSort(null);
        dto.setName("   ");

        MenuEntity result = service.update("m2", dto);

        assertThat(result.getSort()).isEqualTo(7);
        assertThat(result.getName()).isEqualTo("名称");
    }

    // ---------- delete ----------

    @Test
    void deleteShouldThrowWhenMenuMissing() {
        doReturn(null).when(service).getById(any(Serializable.class));

        assertThatThrownBy(() -> service.delete("m404"))
                .isInstanceOf(ApiException.class)
                .hasMessage("菜单不存在");
        verify(roleMenuMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void deleteShouldCleanRoleMenuRelationsAndEvictCache() {
        doReturn(menu("m3", "待删", null, 1)).when(service).getById(any(Serializable.class));
        doReturn(true).when(service).removeById(any(Serializable.class));

        service.delete("m3");

        ArgumentCaptor<Wrapper<RoleMenuEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(roleMenuMapper).delete(wrapperCap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
        assertThat(wrapper.getTargetSql()).contains("menu_id");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("m3");
        verify(service).removeById("m3");
        verify(permissionQueryService).evictAll();
    }

    // ---------- listMenusByRoleId ----------

    @Test
    void listMenusByRoleIdShouldReturnEmptyWhenNoRelation() {
        doReturn(List.of()).when(roleMenuMapper).selectList(any(Wrapper.class));

        assertThat(service.listMenusByRoleId("r1")).isEmpty();
        verify(service, never()).listByIds(anyCollection());
    }

    @Test
    void listMenusByRoleIdShouldLoadMenusByIds() {
        doReturn(List.of(roleMenu("r1", "m1"), roleMenu("r1", "m2")))
                .when(roleMenuMapper).selectList(any(Wrapper.class));
        doReturn(List.of(menu("m1", "菜单1", null, 1), menu("m2", "菜单2", null, 2)))
                .when(service).listByIds(anyCollection());

        List<MenuEntity> menus = service.listMenusByRoleId("r1");

        assertThat(menus).hasSize(2);
        ArgumentCaptor<Wrapper<RoleMenuEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(roleMenuMapper).selectList(cap.capture());
        assertThat(((LambdaQueryWrapper<?>) cap.getValue()).getTargetSql()).contains("role_id");
    }

    // ---------- tree ----------

    @Test
    void treeShouldBuildRootsAndSortedChildren() {
        MenuEntity rootB = menu("b", "B 根", null, 2);
        MenuEntity rootA = menu("a", "A 根", "", 1);
        MenuEntity child2 = menu("a2", "A2", "a", 2);
        MenuEntity childNullSort = menu("a1", "A1", "a", null);
        MenuEntity orphan = menu("c", "C 子", "not-exist", 1);
        doReturn(List.of(rootB, rootA, child2, childNullSort, orphan))
                .when(service).list(any(Wrapper.class));

        List<MenuEntity> tree = service.tree();

        // parentId 为 null 或空串都视为根节点，并按 sort 升序（null 最前）
        assertThat(tree).extracting(MenuEntity::getId).containsExactly("a", "b");
        MenuEntity rootATree = tree.get(0);
        assertThat(rootATree.getChildren()).extracting(MenuEntity::getId)
                .containsExactly("a1", "a2");
        assertThat(rootB.getChildren()).isNull();
        assertThat(orphan.getChildren()).isNull();
    }

    @Test
    void treeShouldReturnEmptyListWhenNoMenu() {
        doReturn(List.of()).when(service).list(any(Wrapper.class));

        assertThat(service.tree()).isEmpty();
    }

    // ---------- assignMenusToRole ----------

    @Test
    void assignMenusToRoleShouldThrowWhenRoleIdMissing() {
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setMenuIds(List.of("m1"));

        assertThatThrownBy(() -> service.assignMenusToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和菜单列表");
    }

    @Test
    void assignMenusToRoleShouldThrowWhenMenuIdsNull() {
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setRoleId("r1");

        assertThatThrownBy(() -> service.assignMenusToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和菜单列表");
    }

    @Test
    void assignMenusToRoleShouldThrowWhenMenuIdsEmpty() {
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setRoleId("r1");
        dto.setMenuIds(List.of());

        assertThatThrownBy(() -> service.assignMenusToRole(dto))
                .isInstanceOf(ApiException.class)
                .hasMessage("请指定角色和菜单列表");
        verify(roleMenuMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void assignMenusToRoleShouldRebuildRelationsAndEvictCache() {
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setRoleId("r1");
        dto.setMenuIds(List.of("m1", "m2", "m3"));

        service.assignMenusToRole(dto);

        ArgumentCaptor<Wrapper<RoleMenuEntity>> deleteCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(roleMenuMapper).delete(deleteCap.capture());
        assertThat(((LambdaQueryWrapper<?>) deleteCap.getValue()).getTargetSql()).contains("role_id");

        ArgumentCaptor<RoleMenuEntity> insertCap = ArgumentCaptor.forClass(RoleMenuEntity.class);
        verify(roleMenuMapper, times(3)).insert(insertCap.capture());
        assertThat(insertCap.getAllValues()).extracting(RoleMenuEntity::getMenuId)
                .containsExactly("m1", "m2", "m3");
        assertThat(insertCap.getAllValues()).allSatisfy(rm -> assertThat(rm.getRoleId()).isEqualTo("r1"));
        verify(permissionQueryService).evictAll();
    }
}
