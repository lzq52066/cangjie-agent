package cn.cangjiecloud.core.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RetryExecutor} 单元测试。
 * <p>
 * 纯 JVM 单测：使用可编程计数的 {@link Callable} 桩，延迟参数取极小值避免拖慢用例。
 */
class RetryExecutorTest {

    /** 前 failTimes 次抛异常，之后返回成功的 Callable 桩。 */
    private static Callable<String> flaky(int failTimes, String successValue) {
        AtomicInteger counter = new AtomicInteger();
        return () -> {
            if (counter.getAndIncrement() < failTimes) {
                throw new IllegalStateException("boom-" + counter.get());
            }
            return successValue;
        };
    }

    /** 记录调用次数的计数器版本，便于断言实际执行次数。 */
    private static Callable<String> flakyCounting(int failTimes, AtomicInteger calls) {
        return () -> {
            if (calls.incrementAndGet() <= failTimes) {
                throw new IllegalStateException("boom-" + calls.get());
            }
            return "ok";
        };
    }

    // ---------- 成功路径 ----------

    @Test
    void executeShouldReturnImmediatelyWhenFirstAttemptSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Callable<String> callable = () -> {
            calls.incrementAndGet();
            return "value";
        };

        String result = RetryExecutor.execute(callable, 3, 0L, 2.0, null, "node");

        assertThat(result).isEqualTo("value");
        assertThat(calls).hasValue(1);
    }

    @Test
    void executeShouldReturnValueWhenSuccessAfterSomeRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = RetryExecutor.execute(flakyCounting(2, calls), 3, 0L, 1.0, null, "node");

        assertThat(result).isEqualTo("ok");
        // 首次 + 2 次重试
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeShouldSucceedOnLastAllowedAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        // maxRetries = 2 => 总共最多 3 次执行，失败 2 次后第 3 次成功
        String result = RetryExecutor.execute(flakyCounting(2, calls), 2, 0L, 2.0, null, "node");

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeShouldReturnNullWhenCallableReturnsNull() throws Exception {
        Object result = RetryExecutor.execute(() -> null, 1, 0L, 2.0, null, "node");

        assertThat(result).isNull();
    }

    // ---------- 重试耗尽 ----------

    @Test
    void executeShouldThrowOriginalExceptionWhenRetriesExhausted() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(10, calls), 2, 0L, 1.0, null, "llm-node"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("boom");

        // 首次 + maxRetries 次重试 = 3
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeShouldThrowCheckedExceptionUnwrappedWhenRetriesExhausted() {
        AtomicInteger calls = new AtomicInteger();
        IOException io = new IOException("disk-full");
        Callable<String> callable = () -> {
            calls.incrementAndGet();
            throw io;
        };

        assertThatThrownBy(() -> RetryExecutor.execute(callable, 1, 0L, 2.0, null, "node"))
                .isSameAs(io);

        assertThat(calls).hasValue(2);
    }

    @Test
    void executeShouldFailFastWhenMaxRetriesIsZero() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(5, calls), 0, 0L, 2.0, null, "node"))
                .isInstanceOf(IllegalStateException.class);

        // maxRetries = 0 => 只执行一次，不重试
        assertThat(calls).hasValue(1);
    }

    @Test
    void executeShouldFailFastWhenMaxRetriesIsNegative() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(5, calls), -1, 0L, 2.0, null, "node"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
    }

    // ---------- retryable 谓词 ----------

    @Test
    void executeShouldNotRetryWhenPredicateRejectsException() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(3, calls), 5, 0L, 2.0,
                e -> false, "node"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void executeShouldRetryOnlyMatchingExceptionWhenPredicateProvided() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Callable<String> callable = () -> {
            int n = calls.incrementAndGet();
            if (n <= 2) {
                throw new IOException("transient-" + n);
            }
            return "recovered";
        };

        Predicate<Exception> retryIo = e -> e instanceof IOException;
        String result = RetryExecutor.execute(callable, 3, 0L, 1.0, retryIo, "node");

        assertThat(result).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeShouldStopRetryingWhenPredicateRejectsLaterException() {
        AtomicInteger calls = new AtomicInteger();
        Callable<String> callable = () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw new IOException("transient");
            }
            throw new IllegalStateException("fatal");
        };

        Predicate<Exception> retryIo = e -> e instanceof IOException;

        assertThatThrownBy(() -> RetryExecutor.execute(callable, 5, 0L, 1.0, retryIo, "node"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fatal");

        // 第 1 次 IOException 重试，第 2 次 IllegalStateException 被谓词拒绝 => 共 2 次
        assertThat(calls).hasValue(2);
    }

    @Test
    void executeShouldRetryAllExceptionsWhenPredicateIsNull() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(9, calls), 2, 0L, 2.0, null, "node"))
                .isInstanceOf(IllegalStateException.class);

        // 谓词为 null 表示所有异常都可重试，仍受 maxRetries 约束
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeShouldCallPredicateOncePerFailure() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger predicateCalls = new AtomicInteger();
        Predicate<Exception> counting = e -> {
            predicateCalls.incrementAndGet();
            return true;
        };

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(9, calls), 3, 0L, 1.0, counting, "node"))
                .isInstanceOf(IllegalStateException.class);

        // 4 次失败：前 3 次允许重试，第 4 次因超过 maxRetries 短路（&& 左侧为 false 不调用谓词）
        assertThat(predicateCalls).hasValue(3);
        assertThat(calls).hasValue(4);
    }

    // ---------- 退避（边界） ----------

    @Test
    void executeShouldGrowDelayByBackoffMultiplier() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long start = System.nanoTime();

        // 失败 3 次后成功；退避序列 5 + 10 + 20 = 35ms
        String result = RetryExecutor.execute(flakyCounting(3, calls), 5, 5L, 2.0, null, "node");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(result).isEqualTo("ok");
        // 只断言下界，避免机器抖动导致偶发失败
        assertThat(elapsedMs).isGreaterThanOrEqualTo(30L);
    }

    @Test
    void executeShouldKeepConstantDelayWhenMultiplierIsOne() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long start = System.nanoTime();

        String result = RetryExecutor.execute(flakyCounting(2, calls), 5, 4L, 1.0, null, "node");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(result).isEqualTo("ok");
        // 4 + 4 = 8ms
        assertThat(elapsedMs).isBetween(7L, 1000L);
    }

    @Test
    void executeShouldNotSleepWhenDelayIsZero() throws Exception {
        long start = System.nanoTime();

        String result = RetryExecutor.execute(flaky(2, "fast"), 3, 0L, 1000.0, null, "node");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertThat(result).isEqualTo("fast");
        assertThat(elapsedMs).isLessThan(1000L);
    }

    @Test
    void executeShouldPropagateIllegalArgumentFromNegativeDelay() {
        AtomicInteger calls = new AtomicInteger();

        // Thread.sleep(-1) 抛 IllegalArgumentException，并从 catch 块向外传播
        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(3, calls), 2, -1L, 2.0, null, "node"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void executeShouldTruncateFractionalDelayToLong() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        // 1ms * 0.5 => 0.5 => (long) 0，退化为 0 后保持 0，不会死循环
        String result = RetryExecutor.execute(flakyCounting(3, calls), 5, 1L, 0.5, null, "node");

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(4);
    }

    // ---------- 简化重载 ----------

    @Test
    void simplifiedOverloadShouldDelegateWithDefaultBackoffAndRetryAll() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = RetryExecutor.execute(flakyCounting(2, calls), 3, 0L, "simple-node");

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void simplifiedOverloadShouldPropagateExceptionWhenExhausted() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(flakyCounting(4, calls), 1, 0L, "simple-node"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(2);
    }

    @Test
    void simplifiedOverloadShouldReturnOnFirstSuccess() throws Exception {
        assertThat(RetryExecutor.execute(() -> "hi", 3, 0L, "node")).isEqualTo("hi");
    }

    // ---------- 工具类不可实例化 ----------

    @Test
    void classShouldBeFinalUtilityWithSinglePrivateConstructor() throws Exception {
        assertThat(Modifier.isFinal(RetryExecutor.class.getModifiers())).isTrue();
        Constructor<?>[] constructors = RetryExecutor.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Modifier.isPrivate(constructors[0].getModifiers())).isTrue();
        assertThat(constructors[0].getParameterCount()).isZero();
    }

    @Test
    void privateConstructorShouldBeInvocableOnlyReflectively() throws Exception {
        Constructor<RetryExecutor> ctor = RetryExecutor.class.getDeclaredConstructor();

        // 未打破访问控制时无法直接实例化
        assertThatThrownBy(() -> ctor.newInstance()).isInstanceOf(IllegalAccessException.class);

        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }

    @Test
    void privateConstructorShouldNotDeclareThrows() throws Exception {
        Constructor<RetryExecutor> ctor = RetryExecutor.class.getDeclaredConstructor();
        assertThat(ctor.getExceptionTypes()).isEmpty();
        // 通过反射调用不会因构造器本身抛异常
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isExactlyInstanceOf(RetryExecutor.class);
        assertThatThrownBy(() -> RetryExecutor.class.getDeclaredConstructor(String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void executeShouldBeDeclaredStatically() throws Exception {
        Method full = RetryExecutor.class.getDeclaredMethod("execute", Callable.class, int.class, long.class,
                double.class, Predicate.class, String.class);
        Method simple = RetryExecutor.class.getDeclaredMethod("execute", Callable.class, int.class, long.class,
                String.class);

        assertThat(Modifier.isStatic(full.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(simple.getModifiers())).isTrue();
        assertThat(full.getReturnType()).isEqualTo(Object.class);
        assertThat(simple.getReturnType()).isEqualTo(Object.class);
    }
}
