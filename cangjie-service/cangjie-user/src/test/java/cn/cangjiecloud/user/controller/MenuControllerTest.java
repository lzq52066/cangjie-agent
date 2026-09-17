package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.service.IMenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MenuController} 单元测试：覆盖全部 8 个接口的委托与响应包装。
 */
class MenuControllerTest {

    private IMenuService menuService;
    private MenuController controller;

    @BeforeEach
    void setUp() {
        menuService = mock(IMenuService.class);
        controller = new MenuController(menuService);
    }

    private static MenuEntity menu(String id, String name) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setName(name);
        menu.setType("menu");
        menu.setStatus("active");
        return menu;
    }

    @Test
    void createShouldDelegateAndWrapResult() {
        // 正常场景：新增菜单
        MenuDTO dto = new MenuDTO();
        dto.setName("用户中心");
        MenuEntity saved = menu("m1", "用户中心");
        when(menuService.create(dto)).thenReturn(saved);

        R<MenuEntity> response = controller.create(dto);

        assertThat(response.getData()).isSameAs(saved);
        verify(menuService).create(dto);
    }

    @Test
    void updateShouldPassPathIdToService() {
        // 正常场景：更新菜单
        MenuDTO dto = new MenuDTO();
        dto.setName("新名称");
        MenuEntity updated = menu("m2", "新名称");
        when(menuService.update("m2", dto)).thenReturn(updated);

        assertThat(controller.update("m2", dto).getData()).isSameAs(updated);
        verify(menuService).update("m2", dto);
    }

    @Test
    void deleteShouldDelegateAndReturnOk() {
        // 正常场景：删除菜单
        R<Void> response = controller.delete("m3");

        verify(menuService).delete("m3");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteShouldPropagateServiceException() {
        // 异常场景：菜单不存在
        doThrow(new IllegalStateException("菜单不存在")).when(menuService).delete("none");

        assertThatThrownBy(() -> controller.delete("none"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("菜单不存在");
    }

    @Test
    void getShouldReturnMenuById() {
        // 正常场景：按 ID 查询
        MenuEntity menu = menu("m4", "角色管理");
        when(menuService.getById("m4")).thenReturn(menu);

        assertThat(controller.get("m4").getData()).isSameAs(menu);
    }

    @Test
    void listShouldReturnAllMenus() {
        // 正常场景：扁平列表
        List<MenuEntity> menus = List.of(menu("m5", "菜单1"), menu("m6", "菜单2"));
        when(menuService.list()).thenReturn(menus);

        R<List<MenuEntity>> response = controller.list();

        assertThat(response.getData()).isEqualTo(menus);
        verify(menuService).list();
    }

    @Test
    void listShouldReturnEmptyWhenNoMenu() {
        // 边界场景：无菜单
        when(menuService.list()).thenReturn(List.of());

        assertThat(controller.list().getData()).isEmpty();
    }

    @Test
    void treeShouldReturnNestedMenus() {
        // 正常场景：树形结构，根节点携带 children
        MenuEntity child = menu("c1", "子菜单");
        MenuEntity root = menu("r1", "根菜单");
        root.setChildren(List.of(child));
        when(menuService.tree()).thenReturn(List.of(root));

        R<List<MenuEntity>> response = controller.tree();

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getChildren()).extracting(MenuEntity::getName)
                .containsExactly("子菜单");
    }

    @Test
    void assignMenusShouldDelegateAndReturnOk() {
        // 正常场景：给角色分配菜单
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setRoleId("role1");
        dto.setMenuIds(List.of("m1", "m2"));

        R<Void> response = controller.assignMenus(dto);

        verify(menuService).assignMenusToRole(dto);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void listMenusByRoleShouldDelegate() {
        // 正常场景：查询角色已授权菜单
        List<MenuEntity> menus = List.of(menu("m7", "已授权菜单"));
        when(menuService.listMenusByRoleId("role2")).thenReturn(menus);

        assertThat(controller.listMenusByRole("role2").getData()).isEqualTo(menus);
        verify(menuService).listMenusByRoleId("role2");
    }
}
