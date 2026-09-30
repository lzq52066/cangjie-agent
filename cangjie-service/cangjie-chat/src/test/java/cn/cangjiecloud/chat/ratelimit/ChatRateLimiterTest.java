package cn.cangjiecloud.chat.ratelimit;

import cn.cangjiecloud.common.exception.ApiException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ChatRateLimiter} 单元测试：覆盖禁用直通、正常放行、突发容量耗尽后拒绝，
 * 以及不同应用维度互不影响。
 */
class ChatRateLimiterTest {

    @Nested
    class Disabled {

        /** 限流禁用时任何请求都直接放行 */
        @Test
        void shouldPassThroughWhenDisabled() {
            ChatRateLimiter limiter = new ChatRateLimiter(false, 1, 1);

            for (int i = 0; i < 100; i++) {
                assertThatCode(() -> limiter.checkRateLimit("app-1")).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class Enabled {

        /** 突发容量内的请求全部放行 */
        @Test
        void shouldAllowRequestsWithinBurst() {
            ChatRateLimiter limiter = new ChatRateLimiter(true, 0.0001, 5);

            assertThatCode(() -> {
                for (int i = 0; i < 5; i++) {
                    limiter.checkRateLimit("app-1");
                }
            }).doesNotThrowAnyException();
        }

        /** 突发令牌耗尽后的下一个请求被拒绝并抛出业务异常 */
        @Test
        void shouldRejectWhenBurstExhausted() {
            ChatRateLimiter limiter = new ChatRateLimiter(true, 0.0001, 3);
            for (int i = 0; i < 3; i++) {
                limiter.checkRateLimit("app-1");
            }

            assertThatThrownBy(() -> limiter.checkRateLimit("app-1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("请求过于频繁，已触发限流，请稍后重试");
        }

        /** 不同应用拥有独立桶：一个耗尽不影响另一个 */
        @Test
        void shouldIsolateBucketsByApplication() {
            ChatRateLimiter limiter = new ChatRateLimiter(true, 0.0001, 1);
            limiter.checkRateLimit("app-1");

            assertThatThrownBy(() -> limiter.checkRateLimit("app-1"))
                    .isInstanceOf(ApiException.class);
            assertThatCode(() -> limiter.checkRateLimit("app-2")).doesNotThrowAnyException();
        }
    }
}
