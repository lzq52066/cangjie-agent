package cn.cangjiecloud.model.security;

import cn.cangjiecloud.common.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * API Key 加解密与脱敏
 * <p>
 * 模型凭证与厂商凭证共用同一套 AES 密钥（{@code cangjie.security.model-key-secret}），
 * 数据库始终存储密文，对外展示统一脱敏。
 */
@Slf4j
@Component
public class ApiKeyCipher {

    @Value("${cangjie.security.model-key-secret:cangjie-model-key-change-me-pls}")
    private String keySecret;

    /**
     * 加密（空值原样返回）
     */
    public String encrypt(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return AesUtil.encrypt(apiKey, keySecret);
    }

    /**
     * 解密；兼容存量明文数据（解密失败原样返回）
     */
    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        try {
            return AesUtil.decrypt(stored, keySecret);
        } catch (Exception e) {
            log.debug("API Key 非密文格式，按明文处理（存量数据兼容）");
            return stored;
        }
    }

    /**
     * 解密后掩码，用于对外展示
     */
    public String mask(String stored) {
        String plain = decrypt(stored);
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return "****" + plain.substring(plain.length() - 4);
    }
}
