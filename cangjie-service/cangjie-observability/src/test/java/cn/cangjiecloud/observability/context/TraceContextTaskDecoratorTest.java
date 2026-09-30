package cn.cangjiecloud.observability.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TraceContextTaskDecorator} 单元测试。
 * <p>
 * 重点验证提交线程的上下文在装饰后的任务中可见（跨线程传递），
 * 以及任务执行结束后线程池线程被复用时的现场恢复与清理。
 */
class TraceContextTaskDecoratorTest {

    private final TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();

    @BeforeEach
    void setUp() {
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Nested
    class CapturedOnSubmit {

        @Test
        void shouldCaptureContextAtDecorateTimeNotAtRunTime() {
            TraceContext.setTraceId("trace-1");
            TraceContext.setSessionId("session-1");
            TraceContext.setUserId("user-1");

            AtomicReference<String> trace = new AtomicReference<>();
            AtomicReference<String> session = new AtomicReference<>();
            AtomicReference<String> user = new AtomicReference<>();
            Runnable decorated = decorator.decorate(() -> {
                trace.set(TraceContext.getTraceId());
                session.set(TraceContext.getSessionId());
                user.set(TraceContext.getUserId());
            });
            // 提交之后再修改提交线程的上下文，不应影响已装饰的任务
            TraceContext.setTraceId("trace-changed");
            TraceContext.setSessionId("session-changed");
            TraceContext.setUserId("user-changed");

            decorated.run();

            assertThat(trace.get()).isEqualTo("trace-1");
            assertThat(session.get()).isEqualTo("session-1");
            assertThat(user.get()).isEqualTo("user-1");
            assertThat(TraceContext.getTraceId()).isEqualTo("trace-changed");
        }

        @Test
        void shouldNotOverrideExistingValuesWhenCapturedValueIsNull() {
            Runnable decorated = decorator.decorate(() -> {
            });

            TraceContext.setTraceId("worker-old");
            TraceContext.setSessionId("worker-old");
            TraceContext.setUserId("worker-old");
            decorated.run();

            assertThat(TraceContext.getTraceId()).isEqualTo("worker-old");
            assertThat(TraceContext.getSessionId()).isEqualTo("worker-old");
            assertThat(TraceContext.getUserId()).isEqualTo("worker-old");
        }
    }

    @Nested
    class RestoreAfterRun {

        @Test
        void shouldExposeCapturedValuesInsideRunAndRestoreWorkerContextAfterwards() {
            TraceContext.setTraceId("trace-1");
            TraceContext.setSessionId("session-1");
            TraceContext.setUserId("user-1");
            AtomicReference<String> inside = new AtomicReference<>();
            Runnable decorated = decorator.decorate(() -> {
                inside.set(TraceContext.getTraceId() + "/" + TraceContext.getSessionId()
                        + "/" + TraceContext.getUserId());
                // 任务内部对上下文的修改在结束后不得残留
                TraceContext.setTraceId("inner-trace");
            });

            // 模拟线程池线程被复用：执行前带着上一轮任务的旧现场
            TraceContext.clear();
            TraceContext.setTraceId("worker-old");
            TraceContext.setSessionId("worker-old");
            TraceContext.setUserId("worker-old");

            decorated.run();

            assertThat(inside.get()).isEqualTo("trace-1/session-1/user-1");
            assertThat(TraceContext.getTraceId()).isEqualTo("worker-old");
            assertThat(TraceContext.getSessionId()).isEqualTo("worker-old");
            assertThat(TraceContext.getUserId()).isEqualTo("worker-old");
        }

        @Test
        void shouldClearContextWhenRunThrows() {
            TraceContext.setTraceId("trace-1");
            Runnable decorated = decorator.decorate(() -> {
                throw new IllegalStateException("boom");
            });

            TraceContext.clear();

            assertThatThrownBy(decorated::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");
            assertThat(TraceContext.getTraceId()).isNull();
            assertThat(TraceContext.getSessionId()).isNull();
            assertThat(TraceContext.getUserId()).isNull();
        }
    }

    @Nested
    class AcrossRealThreadPool {

        @Test
        void shouldPropagateIntoThreadPoolThread() throws Exception {
            TraceContext.setTraceId("trace-pool");
            TraceContext.setSessionId("session-pool");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<String> captured = new AtomicReference<>();
                Future<?> future = executor.submit(
                        decorator.decorate(() -> captured.set(
                                TraceContext.getTraceId() + "/" + TraceContext.getSessionId())));
                future.get(5, TimeUnit.SECONDS);

                assertThat(captured.get()).isEqualTo("trace-pool/session-pool");
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void shouldNotPolluteReusedPoolThread() throws Exception {
            TraceContext.setTraceId("trace-first");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(decorator.decorate(() -> {
                })).get(5, TimeUnit.SECONDS);

                TraceContext.clear();
                Future<String> second = executor.submit(() -> TraceContext.getTraceId() + "");

                assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("null");
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
