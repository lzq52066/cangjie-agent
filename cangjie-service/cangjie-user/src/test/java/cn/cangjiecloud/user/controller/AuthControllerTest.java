package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthController} 单元测试：不启动 MVC，仅验证 Controller 对
 * {@link IUserService} 的直接委托以及统一响应体 {@code R} 的包装。
 */
class AuthControllerTest {

    private IUserService userService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);
        controller = new AuthController(userService);
    }

    @Test
    void loginShouldWrapServiceResultInSuccessResponse() {
        // 正常场景：登录结果原样包装进 R.data
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        Map<String, Object> loginResult = new LinkedHashMap<>();
        loginResult.put("token", "token-abc");
        loginResult.put("tokenName", "satoken");
        when(userService.login(dto)).thenReturn(loginResult);

        R<Map<String, Object>> response = controller.login(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(loginResult);
        assertThat(response.getData()).containsEntry("token", "token-abc");
        verify(userService).login(dto);
    }

    @Test
    void loginShouldPropagateServiceException() {
        // 异常场景：Service 抛出的业务异常由全局异常处理器接管，Controller 不做吞异常处理
        LoginDTO dto = new LoginDTO();
        doThrow(new IllegalStateException("用户名或密码错误")).when(userService).login(dto);

        assertThatThrownBy(() -> controller.login(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void logoutShouldInvokeServiceAndReturnOk() {
        // 正常场景：退出登录返回无数据的成功响应
        R<Void> response = controller.logout();

        verify(userService).logout();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void infoShouldReturnCurrentIdentity() {
        // 正常场景：返回当前登录用户身份
        UserIdentity identity = UserIdentity.builder().userId("u1").username("alice").build();
        when(userService.getCurrentIdentity()).thenReturn(identity);

        R<UserIdentity> response = controller.info();

        assertThat(response.getData()).isSameAs(identity);
        verify(userService).getCurrentIdentity();
    }

    @Test
    void infoShouldReturnNullDataWhenNotLogin() {
        // 边界场景：未登录时 Service 返回 null，Controller 仍返回成功响应且 data 为空
        when(userService.getCurrentIdentity()).thenReturn(null);

        R<UserIdentity> response = controller.info();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void keepAliveShouldReturnTrue() {
        // 正常场景：心跳续期固定返回 true
        R<Boolean> response = controller.keepAlive();

        assertThat(response.getData()).isTrue();
        assertThat(response.isSuccess()).isTrue();
    }
}
