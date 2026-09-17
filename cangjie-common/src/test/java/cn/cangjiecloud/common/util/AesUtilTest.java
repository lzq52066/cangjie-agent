package cn.cangjiecloud.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES-CBC 加解密工具 {@link AesUtil} 单元测试。
 */
class AesUtilTest {

    private static final String SECRET = "my-secret-key";

    @Test
    void encryptThenDecryptRoundTrip() {
        String plain = "sk-test-1234567890-中文";
        String cipher = AesUtil.encrypt(plain, SECRET);
        assertTrue(cipher.length() > plain.length());
        assertEquals(plain, AesUtil.decrypt(cipher, SECRET));
    }

    @Test
    void emptyAndNullShouldPassThrough() {
        assertEquals("", AesUtil.encrypt("", SECRET));
        assertEquals("", AesUtil.decrypt("", SECRET));
        assertEquals(null, AesUtil.encrypt(null, SECRET));
        assertEquals(null, AesUtil.decrypt(null, SECRET));
    }

    @Test
    void samePlainTextEncryptsToDifferentCipherEachTime() {
        // IV 随机，两次密文应不同，但都能解回原文
        String a = AesUtil.encrypt("same", SECRET);
        String b = AesUtil.encrypt("same", SECRET);
        assertNotEquals(a, b);
        assertEquals("same", AesUtil.decrypt(a, SECRET));
        assertEquals("same", AesUtil.decrypt(b, SECRET));
    }

    @Test
    void decryptWithWrongSecretShouldFail() {
        String cipher = AesUtil.encrypt("top-secret", SECRET);
        assertThrows(IllegalStateException.class, () -> AesUtil.decrypt(cipher, "other-secret"));
    }

    @Test
    void decryptTooShortCipherShouldFail() {
        // Base64 解码后长度 <= IV(16) 视为非法
        String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[10]);
        assertThrows(IllegalStateException.class, () -> AesUtil.decrypt(tooShort, SECRET));
    }

    @Test
    void decryptInvalidBase64ShouldFail() {
        assertThrows(IllegalStateException.class, () -> AesUtil.decrypt("not-valid-base64!!", SECRET));
    }
}
