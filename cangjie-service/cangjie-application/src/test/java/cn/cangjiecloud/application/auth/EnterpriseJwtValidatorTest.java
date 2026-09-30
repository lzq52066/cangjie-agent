package cn.cangjiecloud.application.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EnterpriseJwtValidator} 企业 OIDC JWT 验签器单元测试。
 * <p>
 * 全程无网络：本地生成 RSA 密钥对并自签 JWT，将 {@link JWKSource}（生产实现为
 * RemoteJWKSet）替换为 Mockito mock 注入，覆盖签名、iss、aud、过期时间等校验分支。
 * </p>
 */
class EnterpriseJwtValidatorTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String JWKS_URI = "https://idp.example.com/.well-known/jwks.json";
    private static final String AUDIENCE = "cangjie";

    private OidcAuthProperties properties;
    private EnterpriseJwtValidator validator;

    @BeforeEach
    void setUp() {
        properties = new OidcAuthProperties();
        properties.setEnabled(true);
        properties.setIssuer(ISSUER);
        properties.setJwksUri(JWKS_URI);
        properties.setAudience(AUDIENCE);
        validator = new EnterpriseJwtValidator(properties);
    }

    /** 生成一对 RSA 签名密钥 */
    private static RSAKey rsaKey(String id) throws Exception {
        return new RSAKeyGenerator(2048).keyID(id).generate();
    }

    /** 用指定私钥按给定 claims 签发 JWT */
    private static String sign(RSAKey signingKey, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000));
    }

    /** 将公钥集合替换为本地 JWK 源，避免远程拉取 JWKS */
    @SuppressWarnings("unchecked")
    private void stubJwkSource(JWK... keys) throws Exception {
        JWKSource<SecurityContext> source = mock(JWKSource.class);
        when(source.get(any(JWKSelector.class), any())).thenReturn(List.of(keys));
        ReflectionTestUtils.setField(validator, "jwkSource", source);
    }

    @Nested
    class Enablement {

        @Test
        void shouldBeEnabledWhenSwitchIssuerAndJwksUriPresent() {
            assertThat(validator.isEnabled()).isTrue();
        }

        @Test
        void shouldBeDisabledWhenSwitchOff() {
            properties.setEnabled(false);

            assertThat(validator.isEnabled()).isFalse();
        }

        @Test
        void shouldBeDisabledWhenIssuerMissing() {
            properties.setIssuer("  ");

            assertThat(validator.isEnabled()).isFalse();
        }

        @Test
        void shouldBeDisabledWhenJwksUriMissing() {
            properties.setJwksUri(null);

            assertThat(validator.isEnabled()).isFalse();
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldReturnSubjectWhenSignatureAndClaimsValid() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());

            String subject = validator.validate(sign(key, validClaims().build()));

            assertThat(subject).isEqualTo("user-1");
        }

        @Test
        void shouldReturnNullWhenValidatorDisabled() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            properties.setEnabled(false);

            assertThat(validator.validate(sign(key, validClaims().build()))).isNull();
        }

        @Test
        void shouldReturnNullWhenTokenBlank() {
            assertThat(validator.validate(null)).isNull();
            assertThat(validator.validate("   ")).isNull();
        }

        @Test
        void shouldReturnNullWhenTokenNotParseable() {
            assertThat(validator.validate("not-a-jwt")).isNull();
        }

        @Test
        void shouldReturnNullWhenIssuerMismatch() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims().issuer("https://evil.example.com").build();

            assertThat(validator.validate(sign(key, claims))).isNull();
        }

        @Test
        void shouldReturnNullWhenAudienceMissingButRequired() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims().audience((String) null).build();

            assertThat(validator.validate(sign(key, claims))).isNull();
        }

        @Test
        void shouldReturnNullWhenAudienceNotInList() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims().audience("other-service").build();

            assertThat(validator.validate(sign(key, claims))).isNull();
        }

        @Test
        void shouldAcceptTokenWhenAudienceCheckSkipped() throws Exception {
            properties.setAudience(null);
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims().audience((String) null).build();

            assertThat(validator.validate(sign(key, claims))).isEqualTo("user-1");
        }

        @Test
        void shouldRejectExpiredToken() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims()
                    .expirationTime(new Date(System.currentTimeMillis() - 1_000))
                    .build();

            assertThat(validator.validate(sign(key, claims))).isNull();
        }

        @Test
        void shouldReturnNullWhenExpirationMissing() throws Exception {
            RSAKey key = rsaKey("k1");
            stubJwkSource(key.toPublicJWK());
            JWTClaimsSet claims = validClaims().expirationTime(null).build();

            assertThat(validator.validate(sign(key, claims))).isNull();
        }

        @Test
        void shouldReturnNullWhenNoMatchingKey() throws Exception {
            stubJwkSource();
            RSAKey key = rsaKey("k1");

            assertThat(validator.validate(sign(key, validClaims().build()))).isNull();
        }

        @Test
        void shouldReturnNullWhenSignatureKeyDiffers() throws Exception {
            RSAKey signingKey = rsaKey("signing");
            RSAKey otherKey = rsaKey("other");
            // JWKS 只提供另一把公钥，签名无法验过
            stubJwkSource(otherKey.toPublicJWK());

            assertThat(validator.validate(sign(signingKey, validClaims().build()))).isNull();
        }
    }
}
