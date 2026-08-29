package cn.cangjiecloud.model.circuitbreaker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型熔断器（轻量内存实现）
 * <p>
 * 按模型维度统计连续失败次数：达到阈值后进入熔断（open），
 * 冷却期结束后放行探测请求（half-open），成功则恢复，失败则继续熔断。
 * 单实例内存状态，重启后重置。
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    /** 连续失败多少次后熔断 */
    @Value("${cangjie.model.breaker.failure-threshold:5}")
    private int failureThreshold;

    /** 熔断冷却时长（秒） */
    @Value("${cangjie.model.breaker.cooldown-seconds:30}")
    private long cooldownSeconds;

    /** 是否启用熔断 */
    @Value("${cangjie.model.breaker.enabled:true}")
    private boolean enabled;

    private final Map<String, ModelState> states = new ConcurrentHashMap<>();

    /**
     * 是否允许向该模型发起请求（熔断中返回 false；冷却结束返回 true 并进入半开）
     */
    public boolean allowRequest(String modelId) {
        if (!enabled || modelId == null) {
            return true;
        }
        ModelState state = states.get(modelId);
        if (state == null || state.openUntilMs == 0) {
            return true;
        }
        if (System.currentTimeMillis() < state.openUntilMs) {
            return false;
        }
        // 冷却结束：进入半开，放行探测请求
        state.openUntilMs = 0;
        log.info("模型熔断进入半开状态，放行探测请求: {}", modelId);
        return true;
    }

    /**
     * 记录一次调用成功：清零失败计数，恢复闭合
     */
    public void recordSuccess(String modelId) {
        if (!enabled || modelId == null) {
            return;
        }
        ModelState state = states.get(modelId);
        if (state != null) {
            state.failures.set(0);
            state.openUntilMs = 0;
        }
    }

    /**
     * 记录一次调用失败：累计失败次数，达阈值则熔断
     */
    public void recordFailure(String modelId) {
        if (!enabled || modelId == null) {
            return;
        }
        ModelState state = states.computeIfAbsent(modelId, k -> new ModelState());
        int failures = state.failures.incrementAndGet();
        if (failures >= failureThreshold && state.openUntilMs == 0) {
            state.openUntilMs = System.currentTimeMillis() + cooldownSeconds * 1000;
            log.warn("模型熔断开启: modelId={}, 连续失败 {} 次, 冷却 {} 秒",
                    modelId, failures, cooldownSeconds);
        }
    }

    private static final class ModelState {
        final AtomicInteger failures = new AtomicInteger(0);
        volatile long openUntilMs = 0;
    }
}
