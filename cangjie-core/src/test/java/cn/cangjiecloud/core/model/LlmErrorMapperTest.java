package cn.cangjiecloud.core.model;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LlmErrorMapper} 单元测试。
 */
class LlmErrorMapperTest {

    @Nested
    class ClassifyByType {

        @Test
        void shouldClassifyRateLimitException() {
            assertThat(LlmErrorMapper.classify(new RateLimitException("429")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.RATE_LIMIT);
        }

        @Test
        void shouldClassifyAuthenticationException() {
            assertThat(LlmErrorMapper.classify(new AuthenticationException("bad key")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.AUTHENTICATION);
        }

        @Test
        void shouldClassifyTimeoutException() {
            assertThat(LlmErrorMapper.classify(new TimeoutException("slow")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.TIMEOUT);
        }

        @Test
        void shouldClassifyInternalServerException() {
            assertThat(LlmErrorMapper.classify(new InternalServerException("500")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.SERVER_ERROR);
        }

        @Test
        void shouldClassifyModelNotFoundAndContentFiltered() {
            assertThat(LlmErrorMapper.classify(new ModelNotFoundException("x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.MODEL_NOT_FOUND);
            assertThat(LlmErrorMapper.classify(new ContentFilteredException("x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.CONTENT_FILTERED);
        }

        @Test
        void shouldClassifyUnresolvedServerAsNetwork() {
            assertThat(LlmErrorMapper.classify(new UnresolvedModelServerException("unreachable")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.NETWORK);
        }
    }

    @Nested
    class ClassifyByStatusCode {

        @Test
        void shouldMapStatusCodes() {
            assertThat(LlmErrorMapper.classify(new HttpException(503, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.SERVER_ERROR);
            assertThat(LlmErrorMapper.classify(new HttpException(401, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.AUTHENTICATION);
            assertThat(LlmErrorMapper.classify(new HttpException(403, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.AUTHENTICATION);
            assertThat(LlmErrorMapper.classify(new HttpException(404, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.MODEL_NOT_FOUND);
            assertThat(LlmErrorMapper.classify(new HttpException(408, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.TIMEOUT);
            assertThat(LlmErrorMapper.classify(new HttpException(429, "x")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.RATE_LIMIT);
        }

        @Test
        void shouldReturnUnknownForUnmappedStatusCode() {
            assertThat(LlmErrorMapper.classify(new HttpException(418, "teapot")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.UNKNOWN);
        }
    }

    @Nested
    class ClassifyByText {

        @Test
        void shouldClassifyByMessagePatterns() {
            assertThat(LlmErrorMapper.map("Insufficient Balance, please top up"))
                    .startsWith("模型账号余额不足");
            assertThat(LlmErrorMapper.classify(new RuntimeException("Too Many Requests")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.RATE_LIMIT);
            assertThat(LlmErrorMapper.classify(new RuntimeException("invalid api key provided")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.AUTHENTICATION);
            assertThat(LlmErrorMapper.classify(new RuntimeException("maximum context length exceeded")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.CONTEXT_LENGTH);
            assertThat(LlmErrorMapper.classify(new RuntimeException("connection refused by peer")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.NETWORK);
            assertThat(LlmErrorMapper.classify(new RuntimeException("quota exceeded for this month")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.QUOTA_EXCEEDED);
            assertThat(LlmErrorMapper.classify(new RuntimeException("content policy violation")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.CONTENT_FILTERED);
        }

        @Test
        void shouldReturnUnknownForUnrecognizedText() {
            assertThat(LlmErrorMapper.classify(new RuntimeException("something odd happened")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.UNKNOWN);
        }

        @Test
        void shouldReturnUnknownForBlankOrNull() {
            assertThat(LlmErrorMapper.classify(null)).isEqualTo(LlmErrorMapper.LlmErrorKind.UNKNOWN);
            assertThat(LlmErrorMapper.classify(new RuntimeException(" ")))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.UNKNOWN);
        }
    }

    @Nested
    class CauseUnwrapping {

        @Test
        void shouldFindLangChain4jCauseThroughWrappers() {
            Exception wrapped = new RuntimeException("outer",
                    new IllegalStateException("middle", new RateLimitException("429")));

            assertThat(LlmErrorMapper.classify(wrapped))
                    .isEqualTo(LlmErrorMapper.LlmErrorKind.RATE_LIMIT);
        }
    }

    @Nested
    class Retriable {

        @Test
        void shouldMarkTransientFaultsAsRetriable() {
            assertThat(LlmErrorMapper.isRetriable(new RateLimitException("x"))).isTrue();
            assertThat(LlmErrorMapper.isRetriable(new InternalServerException("x"))).isTrue();
            assertThat(LlmErrorMapper.isRetriable(new TimeoutException("x"))).isTrue();
            assertThat(LlmErrorMapper.isRetriable(new UnresolvedModelServerException("x"))).isTrue();
        }

        @Test
        void shouldMarkPermanentFaultsAsNotRetriable() {
            assertThat(LlmErrorMapper.isRetriable(new AuthenticationException("x"))).isFalse();
            assertThat(LlmErrorMapper.isRetriable(new ModelNotFoundException("x"))).isFalse();
            assertThat(LlmErrorMapper.isRetriable(new ContentFilteredException("x"))).isFalse();
        }
    }

    @Nested
    class FriendlyMessage {

        @Test
        void mapThrowableShouldPrefixChineseHintWithRawMessage() {
            String message = LlmErrorMapper.map(new RateLimitException("429 Too Many Requests"));

            assertThat(message).isEqualTo("模型调用频率超限，请稍后重试（429 Too Many Requests）");
        }

        @Test
        void mapStringShouldPrefixChineseHint() {
            assertThat(LlmErrorMapper.map("Request timed out"))
                    .isEqualTo("模型调用超时，请重试（Request timed out）");
        }

        @Test
        void unknownKindShouldEchoRawMessage() {
            assertThat(LlmErrorMapper.map(new RuntimeException("weird error")))
                    .isEqualTo("weird error");
        }

        @Test
        void shouldReturnHintOnlyWhenRawMissing() {
            assertThat(LlmErrorMapper.map(new RateLimitException("")))
                    .isEqualTo("模型调用频率超限，请稍后重试");
            assertThat(LlmErrorMapper.map((String) null))
                    .isEqualTo("未知错误");
        }
    }
}
