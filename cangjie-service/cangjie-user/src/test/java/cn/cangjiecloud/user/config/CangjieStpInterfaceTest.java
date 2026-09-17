package cn.cangjiecloud.user.config;

import cn.cangjiecloud.user.service.PermissionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CangjieStpInterface} 单元测试：验证 sa-token 权限/角色回调对
 * {@link PermissionQueryService} 的委托逻辑，纯 Mockito，不加载 Spring 上下文。
 */
class CangjieStpInterfaceTest {

    private PermissionQueryService permissionQueryService;
    private CangjieStpInterface stpInterface;

    @BeforeEach
    void setUp() {
        permissionQueryService = mock(PermissionQueryService.class);
        stpInterface = new CangjieStpInterface(permissionQueryService);
    }

    @Test
    void getPermissionListShouldDelegateToPermissionQueryService() {
        // 正常场景：按登录用户 ID 查询按钮权限码
        when(permissionQueryService.getPermissionsByUserId("u1"))
                .thenReturn(List.of("system:user:add", "system:user:edit"));

        List<String> permissions = stpInterface.getPermissionList("u1", "login");

        assertThat(permissions).containsExactly("system:user:add", "system:user:edit");
        verify(permissionQueryService).getPermissionsByUserId("u1");
    }

    @Test
    void getPermissionListShouldReturnEmptyWhenUserHasNoPermission() {
        // 边界场景：用户无任何权限
        when(permissionQueryService.getPermissionsByUserId("u2")).thenReturn(List.of());

        assertThat(stpInterface.getPermissionList("u2", "login")).isEmpty();
    }

    @Test
    void getPermissionListShouldPassNullUserId() {
        // 边界场景：未登录（loginId 为 null）时按 null 透传，返回空列表
        when(permissionQueryService.getPermissionsByUserId(null)).thenReturn(List.of());

        assertThat(stpInterface.getPermissionList(null, "login")).isEmpty();
        verify(permissionQueryService).getPermissionsByUserId(null);
    }

    @Test
    void getPermissionListShouldRejectNonStringLoginId() {
        // 异常场景：loginId 非 String 时强转失败，符合源码的 (String) 转换契约
        assertThatThrownBy(() -> stpInterface.getPermissionList(1001L, "login"))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void getRoleListShouldDelegateToRoleCodesQuery() {
        // 正常场景：按登录用户 ID 查询角色编码
        when(permissionQueryService.getRoleCodesByUserId("u3")).thenReturn(List.of("ADMIN"));

        List<String> roles = stpInterface.getRoleList("u3", "login");

        assertThat(roles).containsExactly("ADMIN");
        verify(permissionQueryService).getRoleCodesByUserId("u3");
    }

    @Test
    void getRoleListShouldReturnEmptyWhenUserHasNoRole() {
        // 边界场景：用户未分配任何角色
        when(permissionQueryService.getRoleCodesByUserId("u4")).thenReturn(List.of());

        assertThat(stpInterface.getRoleList("u4", "login")).isEmpty();
    }

    @Test
    void getRoleListShouldRejectNonStringLoginId() {
        // 异常场景：loginId 非 String 时强转失败
        assertThatThrownBy(() -> stpInterface.getRoleList(new Object(), "login"))
                .isInstanceOf(ClassCastException.class);
    }
}
