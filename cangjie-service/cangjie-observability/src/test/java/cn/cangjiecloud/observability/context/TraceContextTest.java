package cn.cangjiecloud.observability.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceContext} 单元测试。
 * <p>
 * 验证基于 ThreadLocal 的 traceId/sessionId/userId 读写互不干扰、线程隔离与 clear 清理。
 */
class TraceContextTest {

    @BeforeEach
    void setUp() {
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Nested
    class ReadWrite {

        @Test
        void shouldReturnNullWhenUnset() {
            assertThat(TraceContext.getTraceId()).isNull();
            assertThat(TraceContext.getSessionId()).isNull();
            assertThat(TraceContext.getUserId()).isNull();
        }

        @Test
        void shouldStoreThreeKeysIndependently() {
            TraceContext.setTraceId("trace-1");
            TraceContext.setSessionId("session-1");
            TraceContext.setUserId("user-1");

            assertThat(TraceContext.getTraceId()).isEqualTo("trace-1");
            assertThat(TraceContext.getSessionId()).isEqualTo("session-1");
            assertThat(TraceContext.getUserId()).isEqualTo("user-1");
        }

        @Test
        void shouldAllowOverwriteAndNullReset() {
            TraceContext.setTraceId("trace-1");
            TraceContext.setTraceId("trace-2");
            TraceContext.setTraceId(null);

            assertThat(TraceContext.getTraceId()).isNull();
        }

        @Test
        void shouldClearAllKeys() {
            TraceContext.setTraceId("trace-1");
            TraceContext.setSessionId("session-1");
            TraceContext.setUserId("user-1");

            TraceContext.clear();

            assertThat(TraceContext.getTraceId()).isNull();
            assertThat(TraceContext.getSessionId()).isNull();
            assertThat(TraceContext.getUserId()).isNull();
        }
    }

    @Nested
    class ThreadIsolation {

        @Test
        void shouldNotLeakToOtherThread() throws Exception {
            TraceContext.setTraceId("main-trace");
            String[] observed = new String[1];

            Thread worker = new Thread(() -> observed[0] = TraceContext.getTraceId());
            worker.start();
            worker.join();

            assertThat(observed[0]).isNull();
            assertThat(TraceContext.getTraceId()).isEqualTo("main-trace");
        }
    }
}
