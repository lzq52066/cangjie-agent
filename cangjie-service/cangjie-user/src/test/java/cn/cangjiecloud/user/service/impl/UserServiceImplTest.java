package cn.cangjiecloud.user.service.impl;

import cn.cangjiecloud.common.api.ResultCode;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.props.SystemProperties;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.service.PermissionQueryService;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link UserServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：spy 拦截继承自 MyBatis-Plus {@code ServiceImpl} 的
 * {@code count/getOne/save/getById} 等方法，Sa-Token 与 UserContext 通过静态 mock 隔离，
 * 密码校验使用真实的 BCrypt 算法（纯内存计算）。
 */
class UserServiceImplTest {

    /** 真实 BCrypt 编码器，仅用于生成测试数据哈希，与被测类内部算法一致 */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String RAW_PASSWORD = "raw-password-123";
    private static String passwordHash;

    private SystemProperties systemProperties;
    private PermissionQueryService permissionQueryService;
    private UserRoleMapper userRoleMapper;
    private UserServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        initTableInfo(UserMapper.class, UserEntity.class);
        initTableInfo(UserRoleMapper.class, UserRoleEntity.class);
        passwordHash = ENCODER.encode(RAW_PASSWORD);
    }

    /** 让 LambdaQueryWrapper 能把 SFunction 解析成列名，无需真实数据库；namespace 只能设置一次，故每个实体各用一个 assistant */
    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        systemProperties = new SystemProperties();
        permissionQueryService = mock(PermissionQueryService.class);
        userRoleMapper = mock(UserRoleMapper.class);
        service = spy(new UserServiceImpl(systemProperties, permissionQueryService, userRoleMapper));
    }

    // ---------- 测试数据构造 ----------

    private static LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private static UserEntity user(String id, String username, String password, Boolean active) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(password);
        u.setNickname("昵称-" + username);
        u.setEmail(username + "@cangjiecloud.cn");
        u.setPhone("18800001111");
        u.setRole(AppConst.ROLE_USER);
        u.setIsActive(active);
        return u;
    }

    /** 默认场景：库里已有 admin（count>0），登录用户存在且密码正确 */
    private UserEntity stubExistingUser(UserEntity user) {
        doReturn(1L).when(service).count(any(Wrapper.class));
        doReturn(user).when(service).getOne(any(Wrapper.class));
        return user;
    }

    // ---------- login ----------

    @Test
    void loginShouldReturnTokenAndIdentityWhenPasswordMatches() {
        UserEntity user = stubExistingUser(user("u1", "alice", passwordHash, true));
        List<String> permissions = List.of("system:user:add", "system:user:delete");
        MenuVO menu = new MenuVO();
        menu.setId("m1");
        menu.setName("用户管理");
        whenMenusAndPermissions(permissions, List.of(menu));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(StpUtil::getTokenValue).thenReturn("token-abc");
            stp.when(StpUtil::getTokenName).thenReturn("satoken");
            stp.when(StpUtil::getSession).thenReturn(session);

            Map<String, Object> result = service.login(loginDTO("alice", RAW_PASSWORD));

            assertThat(result).containsEntry("token", "token-abc")
                    .containsEntry("tokenName", "satoken")
                    .hasSize(3);
            UserIdentity identity = (UserIdentity) result.get("user");
            assertThat(identity.getUserId()).isEqualTo("u1");
            assertThat(identity.getUsername()).isEqualTo("alice");
            assertThat(identity.getNickname()).isEqualTo("昵称-alice");
            assertThat(identity.getEmail()).isEqualTo("alice@cangjiecloud.cn");
            assertThat(identity.getPhone()).isEqualTo("18800001111");
            assertThat(identity.getRole()).isEqualTo(AppConst.ROLE_USER);
            assertThat(identity.getWorkspaceId()).isEqualTo(AppConst.Workspace.DEFAULT_WORKSPACE_ID);
            assertThat(identity.getPermissions()).isEqualTo(permissions);
            assertThat(identity.getMenus()).hasSize(1);

            // 登录写会话：StpUtil.login(userId) + identity/workspaceId 落地
            stp.verify(() -> StpUtil.login("u1"), times(1));
            verify(session).set(eq("identity"), any(UserIdentity.class));
            verify(session).set(eq("workspaceId"), eq(AppConst.Workspace.DEFAULT_WORKSPACE_ID));
        }
        // 已存在默认管理员时不应再创建
        verify(service, never()).save(any(UserEntity.class));
    }

    @Test
    void loginShouldThrowWhenUserNotFound() {
        stubExistingUser(null);

        ApiException ex = catchThrowableOfType(
                () -> service.login(loginDTO("ghost", RAW_PASSWORD)), ApiException.class);

        assertThat(ex).hasMessage("用户名或密码错误");
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILURE);
    }

    @Test
    void loginShouldThrowForbiddenWhenAccountDisabled() {
        stubExistingUser(user("u2", "bob", passwordHash, false));

        ApiException ex = catchThrowableOfType(
                () -> service.login(loginDTO("bob", RAW_PASSWORD)), ApiException.class);

        assertThat(ex).hasMessage("账号已禁用");
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.NO_PERMISSION);
    }

    @Test
    void loginShouldThrowWhenPasswordMismatch() {
        stubExistingUser(user("u3", "carol", passwordHash, true));

        ApiException ex = catchThrowableOfType(
                () -> service.login(loginDTO("carol", "wrong-password")), ApiException.class);

        assertThat(ex).hasMessage("用户名或密码错误");
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILURE);
    }

    @Test
    void loginShouldAllowUserWhenActiveFlagIsNull() {
        // isActive 为 null 时不视为禁用（Boolean.FALSE.equals(null) == false）
        stubExistingUser(user("u4", "dave", passwordHash, null));
        whenMenusAndPermissions(List.of(), List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            stp.when(StpUtil::getTokenValue).thenReturn("t");
            stp.when(StpUtil::getTokenName).thenReturn("n");

            assertThat(service.login(loginDTO("dave", RAW_PASSWORD)).get("token")).isEqualTo("t");
        }
    }

    @Test
    void loginShouldCreateDefaultAdminAndBindAdminRoleWhenNoUserExists() {
        // 空库场景：count == 0，需要初始化默认管理员并绑定 role_admin
        doReturn(0L).when(service).count(any(Wrapper.class));
        doReturn(true).when(service).save(any(UserEntity.class));
        doReturn(null).when(service).getOne(any(Wrapper.class));
        systemProperties.setDefaultUsername("root");
        systemProperties.setDefaultPassword("root@123");
        systemProperties.setDefaultEmail("root@cangjiecloud.cn");
        systemProperties.setDefaultPhone("13900000000");

        ApiException ex = catchThrowableOfType(
                () -> service.login(loginDTO("root", RAW_PASSWORD)), ApiException.class);
        assertThat(ex).hasMessage("用户名或密码错误");

        ArgumentCaptor<UserEntity> userCap = ArgumentCaptor.forClass(UserEntity.class);
        verify(service, times(1)).save(userCap.capture());
        UserEntity admin = userCap.getValue();
        assertThat(admin.getUsername()).isEqualTo("root");
        assertThat(ENCODER.matches("root@123", admin.getPassword())).isTrue();
        assertThat(admin.getNickname()).isEqualTo("超级管理员");
        assertThat(admin.getEmail()).isEqualTo("root@cangjiecloud.cn");
        assertThat(admin.getPhone()).isEqualTo("13900000000");
        assertThat(admin.getRole()).isEqualTo(AppConst.ROLE_ADMIN);
        assertThat(admin.getIsActive()).isTrue();
        assertThat(admin.getSource()).isEqualTo("LOCAL");
        assertThat(admin.getLanguage()).isEqualTo("zh_CN");
        assertThat(admin.getCreateBy()).isEqualTo("system");
        assertThat(admin.getUpdateBy()).isEqualTo("system");
        assertThat(admin.getCreateTime()).isNotNull();
        assertThat(admin.getUpdateTime()).isNotNull();
        assertThat(admin.getDeleted()).isZero();

        ArgumentCaptor<UserRoleEntity> relCap = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleMapper, times(1)).insert(relCap.capture());
        assertThat(relCap.getValue().getRoleId()).isEqualTo("role_admin");
        assertThat(relCap.getValue().getCreateBy()).isEqualTo("system");
    }

    @Test
    void loginShouldQueryDefaultUsernameFromProperties() {
        doReturn(1L).when(service).count(any(Wrapper.class));
        doReturn(null).when(service).getOne(any(Wrapper.class));

        service.login(loginDTO("anyone", RAW_PASSWORD));

        ArgumentCaptor<Wrapper<UserEntity>> countCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).count(countCap.capture());
        assertThat(((LambdaQueryWrapper<?>) countCap.getValue()).getParamNameValuePairs().values())
                .containsExactly(systemProperties.getDefaultUsername());
    }

    // ---------- logout ----------

    @Test
    void logoutShouldDelegateToSaToken() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.logout();
            stp.verify(StpUtil::logout, times(1));
        }
    }

    // ---------- getCurrentIdentity ----------

    @Test
    void getCurrentIdentityShouldReturnCachedIdentityFromContext() {
        UserIdentity cached = UserIdentity.builder().userId("u1").build();
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getIdentity).thenReturn(cached);

            assertThat(service.getCurrentIdentity()).isSameAs(cached);
        }
        verify(service, never()).getById(any(Serializable.class));
    }

    @Test
    void getCurrentIdentityShouldReturnNullWhenNotLoggedIn() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getIdentity).thenReturn(null);
            ctx.when(UserContext::getUserId).thenReturn(null);

            assertThat(service.getCurrentIdentity()).isNull();
            ctx.verify(() -> UserContext.setIdentity(any(UserIdentity.class)), never());
        }
        verify(service, never()).getById(any(Serializable.class));
    }

    @Test
    void getCurrentIdentityShouldReturnNullWhenUserMissing() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getIdentity).thenReturn(null);
            ctx.when(UserContext::getUserId).thenReturn("u9");
            doReturn(null).when(service).getById(any(Serializable.class));

            assertThat(service.getCurrentIdentity()).isNull();
            ctx.verify(() -> UserContext.setIdentity(any(UserIdentity.class)), never());
        }
        verify(permissionQueryService, never()).getPermissionsByUserId(anyString());
    }

    @Test
    void getCurrentIdentityShouldBuildAndCacheIdentityWhenUserExists() {
        UserEntity user = user("u5", "erin", passwordHash, true);
        whenMenusAndPermissions(List.of("p1"), List.of());
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getIdentity).thenReturn(null);
            ctx.when(UserContext::getUserId).thenReturn("u5");
            doReturn(user).when(service).getById(any(Serializable.class));

            UserIdentity identity = service.getCurrentIdentity();

            assertThat(identity).isNotNull();
            assertThat(identity.getUserId()).isEqualTo("u5");
            assertThat(identity.getUsername()).isEqualTo("erin");
            assertThat(identity.getPermissions()).containsExactly("p1");
            assertThat(identity.getMenus()).isEmpty();
            ctx.verify(() -> UserContext.setIdentity(identity), times(1));
        }
    }

    // ---------- getByUsername ----------

    @Test
    void getByUsernameShouldBuildLambdaQueryOnUsernameColumn() {
        UserEntity user = user("u6", "frank", passwordHash, true);
        doReturn(user).when(service).getOne(any(Wrapper.class));

        assertThat(service.getByUsername("frank")).isSameAs(user);

        ArgumentCaptor<Wrapper<UserEntity>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).getOne(cap.capture());
        LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) cap.getValue();
        assertThat(wrapper.getTargetSql()).contains("username");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly("frank");
    }

    @Test
    void getByUsernameShouldReturnNullWhenNoRecord() {
        doReturn(null).when(service).getOne(any(Wrapper.class));

        assertThat(service.getByUsername("nobody")).isNull();
    }

    private void whenMenusAndPermissions(List<String> permissions, List<MenuVO> menus) {
        doReturn(permissions).when(permissionQueryService).getPermissionsByUserId(anyString());
        doReturn(menus).when(permissionQueryService).getMenusByUserId(anyString());
    }
}
