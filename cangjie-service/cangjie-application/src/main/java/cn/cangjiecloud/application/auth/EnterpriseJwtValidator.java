package cn.cangjiecloud.application.auth;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.Key;
import java.util.Date;
import java.util.List;

/**
 * 企业 OIDC JWT 验签器。
 * <p>
 * 仅验签（签名 + iss + aud + 过期时间），不落库、不同步用户。通过返回 token 的 subject 作为
 * 企业用户标识，失败统一返回 {@code null}。JWKS 使用 {@link RemoteJWKSet} 惰性加载并缓存，
 * 自动跟随企业 IdP 轮换密钥。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class EnterpriseJwtValidator {

    private final OidcAuthProperties properties;

    private volatile JWKSource<SecurityContext> jwkSource;

    public boolean isEnabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getIssuer())
                && StringUtils.hasText(properties.getJwksUri());
    }

    /**
     * 校验企业 JWT，通过返回 subject（用户标识），失败返回 null。
     */
    public String validate(String token) {
        if (!isEnabled() || !StringUtils.hasText(token)) {
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSHeader header = jwt.getHeader();
            JWSVerificationKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(header.getAlgorithm(), getJwkSource());
            List<Key> keys = keySelector.selectJWSKeys(header, null);
            DefaultJWSVerifierFactory verifierFactory = new DefaultJWSVerifierFactory();
            for (Key key : keys) {
                JWSVerifier verifier = verifierFactory.createJWSVerifier(header, key);
                if (!jwt.verify(verifier)) {
                    continue;
                }
                JWTClaimsSet claims = jwt.getJWTClaimsSet();
                if (!properties.getIssuer().equals(claims.getIssuer())) {
                    return null;
                }
                if (StringUtils.hasText(properties.getAudience())) {
                    if (claims.getAudience() == null
                            || !claims.getAudience().contains(properties.getAudience())) {
                        return null;
                    }
                }
                Date exp = claims.getExpirationTime();
                if (exp == null || exp.before(new Date())) {
                    return null;
                }
                return claims.getSubject();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private JWKSource<SecurityContext> getJwkSource() throws MalformedURLException {
        JWKSource<SecurityContext> src = jwkSource;
        if (src == null) {
            synchronized (this) {
                src = jwkSource;
                if (src == null) {
                    src = new RemoteJWKSet<>(new URL(properties.getJwksUri()));
                    jwkSource = src;
                }
            }
        }
        return src;
    }
}