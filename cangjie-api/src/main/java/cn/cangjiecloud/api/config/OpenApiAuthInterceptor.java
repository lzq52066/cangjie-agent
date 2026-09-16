package cn.cangjiecloud.api.config;

import cn.cangjiecloud.application.auth.ApiAuthResult;
import cn.cangjiecloud.application.auth.OpenApiAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对外接口统一鉴权拦截器
 * <p>
 * 拦截 /api/open/** 请求，复用 {@link OpenApiAuthenticator} 做统一鉴权（应用 API Key 或企业 OIDC JWT）。
 * 校验通过后将应用实体与（JWT 场景下的）企业用户标识放入 request attribute，供下游 Controller 使用。
 * </p>
 * <p>
 * 网页匿名聊天路径（/api/open/chat*）在 cangjie.openapi.web-anonymous=true 时免 Key 放行，
 * 应用的校验由 {@code OpenWebChatController} 内部完成（GET 走路径参数、POST 走请求体）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    /** request attribute：校验通过后的应用实体 */
    public static final String APPLICATION_ATTR = "openApiApplication";

    /** request attribute：企业 OIDC JWT 验签下的企业用户标识（subject），apikey 场景为 null */
    public static final String PRINCIPAL_ATTR = "openApiPrincipal";

    private final OpenApiAuthenticator authenticator;

    /** 是否允许网页匿名聊天（免 Key） */
    @Value("${cangjie.openapi.web-anonymous:true}")
    private boolean webAnonymousEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 网页匿名聊天：开关开启且路径命中时免 Key 放行
        if (webAnonymousEnabled && isWebAnonymousPath(request.getRequestURI())) {
            return true;
        }

        String token = authenticator.resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "缺少凭证，请在 Authorization: Bearer <token> 或 X-API-Key 头中提供");
            return false;
        }
        ApiAuthResult result = authenticator.authenticate(token);
        if (result == null) {
            writeUnauthorized(response, "凭证无效或应用未发布");
            return false;
        }
        request.setAttribute(APPLICATION_ATTR, result.getApplication());
        request.setAttribute(PRINCIPAL_ATTR, result.getPrincipalId());
        return true;
    }

    /**
     * 判断是否为网页匿名聊天路径。
     * 注意：/api/open/chat/completions 是开发者接口，必须走凭证，不放行。
     */
    private boolean isWebAnonymousPath(String uri) {
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        return uri.equals("/api/open/chat")
                || uri.startsWith("/api/open/chat/stream")
                || uri.startsWith("/api/open/chat/config/")
                || uri.startsWith("/api/open/chat/sessions")
                || uri.startsWith("/api/open/embed");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + HttpServletResponse.SC_UNAUTHORIZED
                + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}