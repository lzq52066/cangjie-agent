package cn.cangjiecloud.application.auth;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对外接口统一鉴权器。
 * <p>
 * 企业用户登录不在本系统、用户也不同步，因此这里不识别"具体的人"，只识别"可信的企业凭证"，
 * 支持两种并存的凭证：
 * </p>
 * <ul>
 *     <li>应用 API Key（方案 A）：企业后端已完成自身 SSO 校验后，带 APKey 服务端到服务端调用；</li>
 *     <li>企业 OIDC JWT（方案 B）：企业用户浏览器直连，携带企业 IdP 签发的 token，由本系统验签。</li>
 * </ul>
 * 两种方式均不会在企业侧与应用侧之间同步用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiAuthenticator {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final IApplicationService applicationService;
    private final EnterpriseJwtValidator jwtValidator;
    private final OidcAuthProperties oidcProperties;

    /**
     * 从请求提取凭据：优先 {@code Authorization: Bearer}，其次 {@code X-API-Key}。
     */
    public String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(auth) && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }

    /**
     * 统一鉴权：先按应用 API Key，再按企业 OIDC JWT。成功返回结果，失败返回 null。
     */
    public ApiAuthResult authenticate(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        // 方案 A：应用 API Key（企业后端到后端）
        ApplicationEntity byApikey = applicationService.getByApikey(token);
        if (byApikey != null) {
            return ApiAuthResult.apikey(byApikey);
        }
        // 方案 B：企业 OIDC JWT（企业用户浏览器直连）
        String subject = jwtValidator.validate(token);
        if (subject != null) {
            ApplicationEntity app = resolveJwtApplication();
            if (app == null) {
                return null;
            }
            return ApiAuthResult.jwt(app, subject);
        }
        return null;
    }

    private ApplicationEntity resolveJwtApplication() {
        String appId = oidcProperties.getAppId();
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        ApplicationEntity app = applicationService.getById(appId);
        if (app == null || !"published".equals(app.getStatus())) {
            return null;
        }
        return app;
    }
}