package cn.cangjiecloud.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-CBC 加解密工具
 * <p>
 * IV 随机生成并前置拼接在密文中（Base64 编码），密钥由配置项派生（SHA-256 取前 16 字节）。
 * 用于模型 API Key 等敏感配置的加密存储。
 */
public final class AesUtil {

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private AesUtil() {
    }

    /**
     * 加密（结果为 Base64 字符串；空值原样返回）
     */
    public static String encrypt(String plainText, String secret) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(secret), "AES"),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密（入参为 encrypt 的产物）
     */
    public static String decrypt(String cipherText, String secret) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(secret), "AES"),
                    new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    private static byte[] deriveKey(String secret) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        return Arrays.copyOf(sha.digest(secret.getBytes(StandardCharsets.UTF_8)), 16);
    }
}
