package cn.cangjiecloud.chat.ratelimit;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.ratelimit.TokenBucketRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话入口限流器：按应用维度限制请求频率，防止单一应用打满模型与系统资源
 */
@Slf4j
@Component
public class ChatRateLimiter {

    private final boolean enabled;
    private final TokenBucketRateLimiter limiter;

    public ChatRateLimiter(@Value("${cangjie.ratelimit.enabled:true}") boolean enabled,
                           @Value("${cangjie.ratelimit.qps:5}") double qps,
                           @Value("${cangjie.ratelimit.burst:10}") double burst) {
        this.enabled = enabled;
        this.limiter = enabled ? new TokenBucketRateLimiter(qps, burst) : null;
        if (enabled) {
            log.info("对话限流已启用: qps={}, burst={}", qps, burst);
        }
    }

    /**
     * 校验应用请求频率，超限抛出业务异常
     */
    public void checkRateLimit(String applicationId) {
        if (!enabled) {
            return;
        }
        if (!limiter.tryAcquire("app:" + applicationId)) {
            throw new ApiException("请求过于频繁，已触发限流，请稍后重试");
        }
    }
}
