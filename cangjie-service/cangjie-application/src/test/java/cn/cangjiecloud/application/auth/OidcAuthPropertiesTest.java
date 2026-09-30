package cn.cangjiecloud.application.auth;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OidcAuthProperties} 配置 POJO 单元测试：覆盖默认值与 Lombok 读写访问器。
 */
class OidcAuthPropertiesTest {

    @Nested
    class Defaults {

        @Test
        void shouldBeDisabledByDefaultAndHaveNoEndpoints() {
            OidcAuthProperties properties = new OidcAuthProperties();

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getIssuer()).isNull();
            assertThat(properties.getJwksUri()).isNull();
            assertThat(properties.getAudience()).isNull();
            assertThat(properties.getAppId()).isNull();
        }
    }

    @Nested
    class Accessors {

        @Test
        void settersShouldUpdateEveryField() {
            OidcAuthProperties properties = new OidcAuthProperties();

            properties.setEnabled(true);
            properties.setIssuer("https://idp.example.com");
            properties.setJwksUri("https://idp.example.com/.well-known/jwks.json");
            properties.setAudience("cangjie");
            properties.setAppId("app-9");

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getIssuer()).isEqualTo("https://idp.example.com");
            assertThat(properties.getJwksUri()).isEqualTo("https://idp.example.com/.well-known/jwks.json");
            assertThat(properties.getAudience()).isEqualTo("cangjie");
            assertThat(properties.getAppId()).isEqualTo("app-9");
        }
    }
}
