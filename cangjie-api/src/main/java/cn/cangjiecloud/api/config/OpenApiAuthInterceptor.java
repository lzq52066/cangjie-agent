package cn.cangjiecloud.api.config;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对外接口统一鉴权拦截器
 * <p>
 * 拦截 /api/open/** 请求，从 Authorization: Bearer &lt;apikey&gt; 头中提取 API Key，
 * 调用 {@link IApplicationService#getByApikey} 校验。校验通过后将应用放入 request attribute，
 * 供下游 Controller 直接使用。
 * </p>
 * <p>
 * 网页匿名聊天路径（/api/open/chat*）在 cangjie.openapi.web-anonymous=true 时免 Key 放行，
 * 应用的校验由 {@code OpenWebChatController} 内部完成（GET 走路径参数、POST 走请求体）。
 * </p>
 */
@RequiredArgsConstructor
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    /** request attribute：校验通过后的应用实体 */
    public static final String APPLICATION_ATTR = "openApiApplication";

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final IApplicationService applicationService;

    /** 是否允许网页匿名聊天（免 API Key），本地部署默认开启 */
    @Value("${cangjie.openapi.web-anonymous:true}")
    private boolean webAnonymousEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 网页匿名聊天：开关开启且路径命中时免 Key 放行
        if (webAnonymousEnabled && isWebAnonymousPath(request.getRequestURI())) {
            return true;
        }

        String apikey = resolveApiKey(request);
        if (!StringUtils.hasText(apikey)) {
            writeUnauthorized(response, "缺少 API Key，请在 Authorization: Bearer <apikey> 头中提供");
            return false;
        }
        ApplicationEntity application = applicationService.getByApikey(apikey);
        if (application == null) {
            writeUnauthorized(response, "API Key 无效或应用未发布");
            return false;
        }
        request.setAttribute(APPLICATION_ATTR, application);
        return true;
    }

    /**
     * 判断是否为网页匿名聊天路径。
     * 注意：/api/open/chat/completions 是开发者接口，必须走 API Key，不放行。
     */
    private boolean isWebAnonymousPath(String uri) {
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        return uri.equals("/api/open/chat")
                || uri.startsWith("/api/open/chat/stream")
                || uri.startsWith("/api/open/chat/config/")
                || uri.startsWith("/api/open/chat/sessions");
    }

    private String resolveApiKey(HttpServletRequest request) {
        String auth = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(auth) && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        // 兼容 X-API-Key 头
        String xApiKey = request.getHeader("X-API-Key");
        return StringUtils.hasText(xApiKey) ? xApiKey.trim() : null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + HttpServletResponse.SC_UNAUTHORIZED
                + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}