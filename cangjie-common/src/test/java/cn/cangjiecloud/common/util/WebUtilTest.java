package cn.cangjiecloud.common.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Web 工具 {@link WebUtil} 单元测试，使用静态 mock 隔离 Sa-Token 与请求上下文。
 */
class WebUtilTest {

    @Test
    void bearerTokenStripsPrefix() {
        SaRequest saRequest = mock(SaRequest.class);
        when(saRequest.getHeader("Authorization")).thenReturn("Bearer abc.def");
        try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
            holder.when(SaHolder::getRequest).thenReturn(saRequest);
            assertEquals("abc.def", WebUtil.getBearerToken());
        }
    }

    @Test
    void tokenWithoutBearerPrefixReturnedAsIs() {
        SaRequest saRequest = mock(SaRequest.class);
        when(saRequest.getHeader("Authorization")).thenReturn("raw-token");
        try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
            holder.when(SaHolder::getRequest).thenReturn(saRequest);
            assertEquals("raw-token", WebUtil.getBearerToken());
        }
    }

    @Test
    void nullTokenYieldsNullBearer() {
        SaRequest saRequest = mock(SaRequest.class);
        when(saRequest.getHeader("Authorization")).thenReturn(null);
        try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
            holder.when(SaHolder::getRequest).thenReturn(saRequest);
            assertNull(WebUtil.getBearerToken());
        }
    }

    @Test
    void getTokenValueFallsBackToRequestWhenSaHolderThrows() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getParameter("token")).thenReturn("from-param");
        try (MockedStatic<SaHolder> holder = mockStatic(SaHolder.class);
             MockedStatic<RequestContextHolder> rch = mockStatic(RequestContextHolder.class)) {
            holder.when(SaHolder::getRequest).thenThrow(new IllegalStateException("no context"));
            rch.when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(new ServletRequestAttributes(req));
            assertEquals("from-param", WebUtil.getTokenValue());
        }
    }

    @Test
    void clientIpPrefersForwardedForFirstEntry() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(" 1.2.3.4 , 5.6.7.8");
        try (MockedStatic<RequestContextHolder> rch = mockStatic(RequestContextHolder.class)) {
            rch.when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(new ServletRequestAttributes(req));
            assertEquals("1.2.3.4", WebUtil.getClientIP());
        }
    }

    @Test
    void clientIpFallsBackToRealIp() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(req.getHeader("X-Real-IP")).thenReturn("9.9.9.9");
        try (MockedStatic<RequestContextHolder> rch = mockStatic(RequestContextHolder.class)) {
            rch.when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(new ServletRequestAttributes(req));
            assertEquals("9.9.9.9", WebUtil.getClientIP());
        }
    }

    @Test
    void clientIpFallsBackToRemoteAddr() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("10.0.0.1");
        try (MockedStatic<RequestContextHolder> rch = mockStatic(RequestContextHolder.class)) {
            rch.when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(new ServletRequestAttributes(req));
            assertEquals("10.0.0.1", WebUtil.getClientIP());
        }
    }

    @Test
    void clientIpEmptyWhenNoRequest() {
        try (MockedStatic<RequestContextHolder> rch = mockStatic(RequestContextHolder.class)) {
            rch.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
            assertEquals("", WebUtil.getClientIP());
        }
    }
}
