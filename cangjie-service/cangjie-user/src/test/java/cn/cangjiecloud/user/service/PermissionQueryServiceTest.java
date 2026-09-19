package cn.cangjiecloud.user.service;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.cangjiecloud.user.mapper.RoleMapper;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionQueryService} 单元测试：直接 mock 五个 Mapper，不加载任何数据源。
 * <p>
 * 覆盖：超级管理员两条判定路径、按钮权限码过滤、角色编码查询、菜单树平铺与排序、缓存清理。
 */
class PermissionQueryServiceTest {

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private RoleMenuMapper roleMenuMapper;
    private MenuMapper menuMapper;
    private RoleMapper roleMapper;
    private PermissionQueryService service;

    @BeforeAll
    static void installTableInfo() {
        initTableInfo(UserMapper.class, UserEntity.class);
        initTableInfo(UserRoleMapper.class, UserRoleEntity.class);
        initTableInfo(RoleMapper.class, RoleEntity.class);
        initTableInfo(RoleMenuMapper.class, RoleMenuEntity.class);
        initTableInfo(MenuMapper.class, MenuEntity.class);
    }

    /** 让 LambdaQueryWrapper 能把 SFunction 解析成列名，无需真实数据库；namespace 只能设置一次，故每个实体各用一个 assistant */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        roleMenuMapper = mock(RoleMenuMapper.class);
        menuMapper = mock(MenuMapper.class);
        roleMapper = mock(RoleMapper.class);
        service = new PermissionQueryService(userMapper, userRoleMapper, roleMenuMapper,
                menuMapper, roleMapper);
    }

    // ---------- 测试数据 ----------

    private static UserEntity user(String id, String role) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private static UserRoleEntity userRole(String userId, String roleId) {
        UserRoleEntity ur = new UserRoleEntity();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        return ur;
    }

    private static RoleMenuEntity roleMenu(String roleId, String menuId) {
        RoleMenuEntity rm = new RoleMenuEntity();
        rm.setRoleId(roleId);
        rm.setMenuId(menuId);
        return rm;
    }

    private static RoleEntity role(String id, String code) {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        r.setCode(code);
        return r;
    }

    private static MenuEntity menu(String id, String code, String type, Integer sort) {
        MenuEntity m = new MenuEntity();
        m.setId(id);
        m.setCode(code);
        m.setType(type);
        m.setSort(sort);
        m.setName("菜单-" + id);
        m.setPath("/" + id);
        m.setComponent("cmp/" + id);
        m.setIcon("icon-" + id);
        m.setStatus("active");
        m.setParentId(sort == null ? "root" : null);
        return m;
    }

    private void stubUserRoles(UserRoleEntity... userRoles) {
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userRoles));
    }

    // ---------- getPermissionsByUserId ----------

    @Test
    void permissionsShouldBypassToAllCodesWhenUserRoleFieldIsAdmin() {
        when(userMapper.selectById(anyString())).thenReturn(user("u1", AppConst.ROLE_ADMIN));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                menu("m1", "sys:user:add", "button", 1),
                menu("m2", "sys:user:add", "button", 2),
                menu("m3", null, "button", 3),
                menu("m4", "system:menu", "menu", 4)));

        List<String> codes = service.getPermissionsByUserId("u1");

        // 仅 button 类型、code 非空且去重
        assertThat(codes).containsExactly("sys:user:add");
        // ADMIN 无需再查角色表
        verify(userRoleMapper, never()).selectList(any(Wrapper.class));
        verify(roleMapper, never()).selectCount(any(Wrapper.class));
    }

    @Test
    void permissionsShouldBypassWhenUserHasAdminRoleRecord() {
        when(userMapper.selectById(anyString())).thenReturn(user("u2", AppConst.ROLE_USER));
        stubUserRoles(userRole("u2", "r1"));
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(menu("m1", "btn:a", "button", 1)));

        assertThat(service.getPermissionsByUserId("u2")).containsExactly("btn:a");
        verify(roleMenuMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void permissionsShouldFallBackToMatrixWhenNotAdmin() {
        when(userMapper.selectById(anyString())).thenReturn(null);
        stubUserRoles(userRole("u3", "r1"));
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(roleMenuMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(roleMenu("r1", "m1"), roleMenu("r1", "m1"), roleMenu("r1", "m2")));
        // mock 不执行 SQL，这里只返回角色已授权菜单（m1/m2）对应的行，模拟 .in(id, menuIds) 的过滤结果
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                menu("m1", "btn:x", "button", 1),
                menu("m2", "menu:y", "menu", 2)));

        List<String> codes = service.getPermissionsByUserId("u3");

        assertThat(codes).containsExactly("btn:x");
        ArgumentCaptor<Wrapper<MenuEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(menuMapper).selectList(cap.capture());
        // 权限矩阵查询按菜单 ID 收敛
        assertThat(((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) cap.getValue())
                .getTargetSql()).contains("id").contains("deleted");
    }

    @Test
    void permissionsShouldBeEmptyWhenUserHasNoRole() {
        when(userMapper.selectById(anyString())).thenReturn(null);
        stubUserRoles();

        assertThat(service.getPermissionsByUserId("u4")).isEmpty();
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void permissionsShouldBeEmptyWhenRoleHasNoMenu() {
        when(userMapper.selectById(anyString())).thenReturn(user("u5", AppConst.ROLE_USER));
        stubUserRoles(userRole("u5", "r1"));
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(roleMenuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.getPermissionsByUserId("u5")).isEmpty();
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

    // ---------- getRoleCodesByUserId ----------

    @Test
    void roleCodesShouldBeEmptyWhenNoRelation() {
        stubUserRoles();

        assertThat(service.getRoleCodesByUserId("u1")).isEmpty();
        verify(roleMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void roleCodesShouldFilterNullCodes() {
        stubUserRoles(userRole("u2", "r1"), userRole("u2", "r2"));
        when(roleMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(role("r1", "ADMIN"), role("r2", null)));

        List<String> codes = service.getRoleCodesByUserId("u2");

        assertThat(codes).containsExactly("ADMIN");
    }

    // ---------- getMenusByUserId ----------

    @Test
    void menusShouldReturnAllSortedWhenAdmin() {
        when(userMapper.selectById(anyString())).thenReturn(user("u1", AppConst.ROLE_ADMIN));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                menu("m2", "b", "menu", 2),
                menu("m1", "a", "menu", 1),
                menu("m3", "c", "menu", null)));

        List<MenuVO> menus = service.getMenusByUserId("u1");

        // sort 升序，null 排在最前
        assertThat(menus).extracting(MenuVO::getSort).containsExactly(null, 1, 2);
        MenuVO first = menus.get(1);
        assertThat(first.getId()).isEqualTo("m1");
        assertThat(first.getName()).isEqualTo("菜单-m1");
        assertThat(first.getPath()).isEqualTo("/m1");
        assertThat(first.getComponent()).isEqualTo("cmp/m1");
        assertThat(first.getIcon()).isEqualTo("icon-m1");
        assertThat(first.getType()).isEqualTo("menu");
        assertThat(first.getStatus()).isEqualTo("active");
        assertThat(first.getParentId()).isNull();
    }

    @Test
    void menusShouldBeEmptyWhenNotAdminWithoutMenuIds() {
        when(userMapper.selectById(anyString())).thenReturn(user("u2", AppConst.ROLE_USER));
        stubUserRoles();
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThat(service.getMenusByUserId("u2")).isEmpty();
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void menusShouldMapVoWhenNotAdminWithMenuIds() {
        when(userMapper.selectById(anyString())).thenReturn(user("u3", AppConst.ROLE_USER));
        stubUserRoles(userRole("u3", "r1"));
        when(roleMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(roleMenuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(roleMenu("r1", "m9")));
        MenuEntity mapped = menu("m9", "btn", "button", 5);
        mapped.setParentId("root");
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mapped));

        List<MenuVO> menus = service.getMenusByUserId("u3");

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getId()).isEqualTo("m9");
        assertThat(menus.get(0).getSort()).isEqualTo(5);
        assertThat(menus.get(0).getParentId()).isEqualTo("root");
    }

    // ---------- evictAll ----------

    @Test
    void evictAllShouldOnlyLogAndTouchNoMapper() {
        assertThatCode(() -> service.evictAll()).doesNotThrowAnyException();
        verify(userMapper, never()).selectById(anyString());
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }
}
