package cn.cangjiecloud.common.context;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户上下文 {@link UserContext} 单元测试，使用静态 mock 隔离 Sa-Token。
 */
class UserContextTest {

    @Test
    void getUserIdReturnsNullWhenNotLoggedIn() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertNull(UserContext.getUserId());
        }
    }

    @Test
    void getUserIdReturnsLoginId() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdDefaultNull).thenReturn("u1");
            assertEquals("u1", UserContext.getUserId());
        }
    }

    @Test
    void workspaceIdFallsBackToDefault() {
        SaSession session = mock(SaSession.class);
        when(session.get("workspaceId")).thenReturn(null);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            assertEquals(AppConst.Workspace.DEFAULT_WORKSPACE_ID, UserContext.getWorkspaceId());
        }
    }

    @Test
    void workspaceIdReadsFromSession() {
        SaSession session = mock(SaSession.class);
        when(session.get("workspaceId")).thenReturn("w9");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            assertEquals("w9", UserContext.getWorkspaceId());
        }
    }

    @Test
    void identityNullWhenAbsent() {
        SaSession session = mock(SaSession.class);
        when(session.get("identity")).thenReturn(null);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            assertNull(UserContext.getIdentity());
        }
    }

    @Test
    void identityParsesFromJsonObject() {
        ObjectNode raw = JsonUtils.newObject();
        raw.put("userId", "u2");
        raw.put("username", "bob");
        SaSession session = mock(SaSession.class);
        when(session.get("identity")).thenReturn(raw);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            UserIdentity id = UserContext.getIdentity();
            assertEquals("u2", id.getUserId());
            assertEquals("bob", id.getUsername());
        }
    }

    @Test
    void identityParsesFromArbitraryObject() {
        UserIdentity src = UserIdentity.builder().userId("u3").workspaceId("w3").build();
        SaSession session = mock(SaSession.class);
        when(session.get("identity")).thenReturn(src);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            UserIdentity id = UserContext.getIdentity();
            assertEquals("u3", id.getUserId());
            assertEquals("w3", id.getWorkspaceId());
        }
    }

    @Test
    void setIdentityWritesIdentityAndWorkspace() {
        SaSession session = mock(SaSession.class);
        UserIdentity id = UserIdentity.builder().userId("u1").workspaceId("w1").build();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(session);
            UserContext.setIdentity(id);
            verify(session).set(eq("identity"), eq(id));
            verify(session).set(eq("workspaceId"), eq("w1"));
        }
    }

    @Test
    void setUserIdDelegatesToLogin() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            UserContext.setUserId("u9");
            stp.verify(() -> StpUtil.login("u9"), times(1));
        }
    }
}
