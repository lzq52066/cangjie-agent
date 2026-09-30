package cn.cangjiecloud.application.auth;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiAuthResult} 鉴权结果值对象单元测试：覆盖工厂方法与字段不可变性。
 */
class ApiAuthResultTest {

    private static ApplicationEntity application() {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId("app-1");
        entity.setName("demo");
        return entity;
    }

    @Test
    void apikeyFactoryShouldCreateApiKeyResultWithoutPrincipal() {
        ApplicationEntity app = application();

        ApiAuthResult result = ApiAuthResult.apikey(app);

        assertThat(result.getType()).isEqualTo(ApiAuthResult.Type.APIKEY);
        assertThat(result.getApplication()).isSameAs(app);
        assertThat(result.getPrincipalId()).isNull();
    }

    @Test
    void jwtFactoryShouldCreateJwtResultWithPrincipal() {
        ApplicationEntity app = application();

        ApiAuthResult result = ApiAuthResult.jwt(app, "user-42");

        assertThat(result.getType()).isEqualTo(ApiAuthResult.Type.JWT);
        assertThat(result.getApplication()).isSameAs(app);
        assertThat(result.getPrincipalId()).isEqualTo("user-42");
    }

    @Test
    void jwtFactoryShouldAllowNullPrincipal() {
        ApiAuthResult result = ApiAuthResult.jwt(application(), null);

        assertThat(result.getType()).isEqualTo(ApiAuthResult.Type.JWT);
        assertThat(result.getPrincipalId()).isNull();
    }

    @Test
    void typeEnumShouldExposeExactlyTwoCredentials() {
        // 凭证类型仅支持应用 API Key 与企业 OIDC JWT 两种
        assertThat(ApiAuthResult.Type.values())
                .containsExactly(ApiAuthResult.Type.APIKEY, ApiAuthResult.Type.JWT);
    }
}
