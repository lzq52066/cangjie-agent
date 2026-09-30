package cn.cangjiecloud.application.auth;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenApiAuthenticator} 对外接口鉴权器单元测试。
 * <p>
 * 不加载 Spring 上下文：应用服务、JWT 验签器、OIDC 配置全部使用 Mockito，
 * 重点验证凭据提取优先级、API Key / JWT 两条鉴权路径的委派逻辑。
 * </p>
 */
class OpenApiAuthenticatorTest {

    private static final String BEARER_TOKEN = "jwt-token";
    private static final String API_KEY = "cj_apikey";

    private IApplicationService applicationService;
    private EnterpriseJwtValidator jwtValidator;
    private OidcAuthProperties oidcProperties;
    private OpenApiAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        applicationService = mock(IApplicationService.class);
        jwtValidator = mock(EnterpriseJwtValidator.class);
        oidcProperties = mock(OidcAuthProperties.class);
        authenticator = new OpenApiAuthenticator(applicationService, jwtValidator, oidcProperties);
    }

    private static ApplicationEntity application(String id, String status) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }

    @Nested
    class TokenExtraction {

        private HttpServletRequest request;

        @BeforeEach
        void setUp() {
            request = mock(HttpServletRequest.class);
        }

        @Test
        void shouldExtractBearerTokenFromAuthorizationHeader() {
            when(request.getHeader("Authorization")).thenReturn("Bearer " + BEARER_TOKEN);

            assertThat(authenticator.resolveToken(request)).isEqualTo(BEARER_TOKEN);
        }

        @Test
        void shouldTrimSurroundingWhitespaceOfBearerToken() {
            when(request.getHeader("Authorization")).thenReturn("Bearer   " + BEARER_TOKEN + "  ");

            assertThat(authenticator.resolveToken(request)).isEqualTo(BEARER_TOKEN);
        }

        @Test
        void shouldFallbackToApiKeyHeaderWhenAuthorizationIsMissing() {
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("X-API-Key")).thenReturn(API_KEY);

            assertThat(authenticator.resolveToken(request)).isEqualTo(API_KEY);
        }

        @Test
        void shouldFallbackToApiKeyHeaderWhenAuthorizationIsNotBearer() {
            when(request.getHeader("Authorization")).thenReturn("Basic abc123");
            when(request.getHeader("X-API-Key")).thenReturn(API_KEY);

            assertThat(authenticator.resolveToken(request)).isEqualTo(API_KEY);
        }

        @Test
        void shouldTrimApiKeyHeaderValue() {
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("X-API-Key")).thenReturn("  " + API_KEY + "  ");

            assertThat(authenticator.resolveToken(request)).isEqualTo(API_KEY);
        }

        @Test
        void shouldReturnNullWhenNoCredentialPresent() {
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getHeader("X-API-Key")).thenReturn(null);

            assertThat(authenticator.resolveToken(request)).isNull();
        }

        @Test
        void shouldReturnNullWhenHeadersAreBlank() {
            when(request.getHeader("Authorization")).thenReturn("   ");
            when(request.getHeader("X-API-Key")).thenReturn("   ");

            assertThat(authenticator.resolveToken(request)).isNull();
        }

        @Test
        void shouldPreferBearerTokenOverApiKey() {
            when(request.getHeader("Authorization")).thenReturn("Bearer " + BEARER_TOKEN);
            when(request.getHeader("X-API-Key")).thenReturn(API_KEY);

            assertThat(authenticator.resolveToken(request)).isEqualTo(BEARER_TOKEN);
        }
    }

    @Nested
    class Authentication {

        @Test
        void shouldReturnNullWhenTokenIsBlank() {
            assertThat(authenticator.authenticate(null)).isNull();
            assertThat(authenticator.authenticate("  ")).isNull();

            verify(applicationService, never()).getByApikey(org.mockito.ArgumentMatchers.anyString());
            verify(jwtValidator, never()).validate(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        void shouldAuthenticateWithApiKeyWhenApplicationFound() {
            ApplicationEntity app = application("app-1", "published");
            when(applicationService.getByApikey(API_KEY)).thenReturn(app);

            ApiAuthResult result = authenticator.authenticate(API_KEY);

            assertThat(result.getType()).isEqualTo(ApiAuthResult.Type.APIKEY);
            assertThat(result.getApplication()).isSameAs(app);
            assertThat(result.getPrincipalId()).isNull();
            // API Key 命中后不再尝试 JWT 验签
            verify(jwtValidator, never()).validate(API_KEY);
        }

        @Test
        void shouldAuthenticateWithJwtWhenTokenIsNotApiKeyAndAppPublished() {
            when(applicationService.getByApikey(BEARER_TOKEN)).thenReturn(null);
            when(jwtValidator.validate(BEARER_TOKEN)).thenReturn("user-1");
            when(oidcProperties.getAppId()).thenReturn("app-2");
            ApplicationEntity app = application("app-2", "published");
            when(applicationService.getById("app-2")).thenReturn(app);

            ApiAuthResult result = authenticator.authenticate(BEARER_TOKEN);

            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(ApiAuthResult.Type.JWT);
            assertThat(result.getApplication()).isSameAs(app);
            assertThat(result.getPrincipalId()).isEqualTo("user-1");
        }

        @Test
        void shouldReturnNullWhenJwtValidButAppIdNotConfigured() {
            when(applicationService.getByApikey(BEARER_TOKEN)).thenReturn(null);
            when(jwtValidator.validate(BEARER_TOKEN)).thenReturn("user-1");
            when(oidcProperties.getAppId()).thenReturn("  ");

            assertThat(authenticator.authenticate(BEARER_TOKEN)).isNull();
            verify(applicationService, never()).getById(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        void shouldReturnNullWhenJwtApplicationMissing() {
            when(applicationService.getByApikey(BEARER_TOKEN)).thenReturn(null);
            when(jwtValidator.validate(BEARER_TOKEN)).thenReturn("user-1");
            when(oidcProperties.getAppId()).thenReturn("app-x");
            when(applicationService.getById("app-x")).thenReturn(null);

            assertThat(authenticator.authenticate(BEARER_TOKEN)).isNull();
        }

        @Test
        void shouldReturnNullWhenJwtApplicationNotPublished() {
            when(applicationService.getByApikey(BEARER_TOKEN)).thenReturn(null);
            when(jwtValidator.validate(BEARER_TOKEN)).thenReturn("user-1");
            when(oidcProperties.getAppId()).thenReturn("app-3");
            when(applicationService.getById("app-3")).thenReturn(application("app-3", "draft"));

            assertThat(authenticator.authenticate(BEARER_TOKEN)).isNull();
        }

        @Test
        void shouldReturnNullWhenJwtValidationFails() {
            when(applicationService.getByApikey(BEARER_TOKEN)).thenReturn(null);
            when(jwtValidator.validate(BEARER_TOKEN)).thenReturn(null);

            assertThat(authenticator.authenticate(BEARER_TOKEN)).isNull();
            verify(oidcProperties, never()).getAppId();
        }
    }
}
