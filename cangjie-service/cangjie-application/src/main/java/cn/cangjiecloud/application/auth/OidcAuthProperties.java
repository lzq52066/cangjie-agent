package cn.cangjiecloud.application.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业 OIDC 单点登录验签配置。
 * <p>
 * 企业内部用户由企业自身 IdP 登录，用户不落库不同步。本系统只验签企业 IdP 签发的
 * access_token / id_token（JWT），校验通过即认为请求来自可信企业用户。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "cangjie.auth.oidc")
public class OidcAuthProperties {

    /** 是否启用企业 OIDC 验签（默认关闭，仅凭应用 API Key 鉴权） */
    private boolean enabled = false;

    /** 令牌签发方（iss），用于校验 iss 声明 */
    private String issuer;

    /** JWKS 公钥地址，用于校验令牌签名 */
    private String jwksUri;

    /** 校验 aud（可选，留空跳过 aud 校验） */
    private String audience;

    /** 企业用户默认映射到的应用 ID（须已发布） */
    private String appId;
}