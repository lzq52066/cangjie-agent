package cn.cangjiecloud.model.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiKeyCipher} 单元测试。
 * <p>
 * 通过 ReflectionTestUtils 注入 AES 密钥，覆盖加解密往返、空值处理、
 * 存量明文兼容（解密失败原样返回）以及对外脱敏规则。
 */
class ApiKeyCipherTest {

    private static final String KEY_SECRET = "unit-test-model-secret";

    private ApiKeyCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new ApiKeyCipher();
        ReflectionTestUtils.setField(cipher, "keySecret", KEY_SECRET);
    }

    @Nested
    class Encryption {

        @Test
        void shouldEncryptToDifferentCiphertextEachTimeDueToRandomIv() {
            String c1 = cipher.encrypt("sk-abcdef123456");
            String c2 = cipher.encrypt("sk-abcdef123456");

            // 随机 IV：相同明文每次密文不同，且都不等于明文
            assertThat(c1).isNotEqualTo(c2);
            assertThat(c1).isNotEqualTo("sk-abcdef123456");
        }

        @Test
        void shouldReturnNullAndBlankValuesAsIs() {
            assertThat(cipher.encrypt(null)).isNull();
            assertThat(cipher.encrypt("")).isEmpty();
            assertThat(cipher.encrypt("   ")).isEqualTo("   ");
        }
    }

    @Nested
    class Decryption {

        @Test
        void shouldRoundTripEncryptedValue() {
            String plain = "sk-secret-key-987654321";

            assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
        }

        @Test
        void shouldReturnNullAndBlankValuesAsIs() {
            assertThat(cipher.decrypt(null)).isNull();
            assertThat(cipher.decrypt("")).isEmpty();
        }

        @Test
        void shouldTreatUndecryptablePlaintextAsLegacyDataAndReturnAsIs() {
            // 存量明文：不是合法 AES 密文（Base64 解码后长度不足/填充错误），解密失败时原样返回
            String legacyPlain = "plain-text-api-key";

            assertThat(cipher.decrypt(legacyPlain)).isEqualTo(legacyPlain);
        }
    }

    @Nested
    class Masking {

        @Test
        void shouldExposeOnlyLastFourCharactersForLongKeys() {
            String plain = "sk-1234567890ABCD";
            String stored = cipher.encrypt(plain);

            // 解密后掩码：仅保留末 4 位
            assertThat(cipher.mask(stored)).isEqualTo("****ABCD");
        }

        @Test
        void shouldMaskPlaintextLegacyDataAsWell() {
            // 明文存量数据同样可以掩码
            assertThat(cipher.mask("a-legacy-plain-key")).isEqualTo("****-key");
        }

        @Test
        void shouldFullyMaskWhenPlainLengthNotGreaterThanEight() {
            // 长度 <= 8 时不保留任何明文片段
            assertThat(cipher.mask("12345678")).isEqualTo("****");
            assertThat(cipher.mask("short")).isEqualTo("****");
        }

        @Test
        void shouldReturnNullAndBlankValuesAsIs() {
            assertThat(cipher.mask(null)).isNull();
            assertThat(cipher.mask("")).isEmpty();
        }
    }
}
