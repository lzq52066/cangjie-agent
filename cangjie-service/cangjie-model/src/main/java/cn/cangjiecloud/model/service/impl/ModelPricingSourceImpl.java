package cn.cangjiecloud.model.service.impl;

import cn.cangjiecloud.core.model.ModelPricing;
import cn.cangjiecloud.core.model.ModelPricingSource;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型单价来源：从 {@code model_config} 读输入 / 输出单价。
 * <p>
 * 带一层 60 秒的短缓存：这一层在每个模型调用结束时被取一次，直查库里没有错，
 * 但「一次对话 2~5 条调用各多一次 DB 往返」正是链路写入要避免的开销；
 * 代价是改完单价最多一分钟后的调用才按新价算 —— 单价不是分秒必争的量。
 * <p>
 * 缓存里连「这个模型没定价」也照样记一笔（{@link #NONE}），否则每次调用都要回库确认同一个坏消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelPricingSourceImpl implements ModelPricingSource {

    /** 单价缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 60_000L;

    /** 已确认「没定价」的占位：与「还没查过」区分开 */
    private static final ModelPricing NONE = new ModelPricing(null, null);

    private final ModelMapper modelMapper;

    private record Entry(ModelPricing pricing, long loadedAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @Override
    public ModelPricing findPricing(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry cached = cache.get(modelId);
        if (cached != null && now - cached.loadedAt() < CACHE_TTL_MS) {
            return cached.pricing() == NONE ? null : cached.pricing();
        }
        ModelPricing pricing = load(modelId);
        // 查库失败按「没有单价」处理：成本估算不了不值得把整条链路记录一起丢掉
        cache.put(modelId, new Entry(pricing == null ? NONE : pricing, now));
        return pricing;
    }

    private ModelPricing load(String modelId) {
        try {
            ModelEntity entity = modelMapper.selectById(modelId);
            if (entity == null || (entity.getInputPrice() == null && entity.getOutputPrice() == null)) {
                return null;
            }
            return new ModelPricing(entity.getInputPrice(), entity.getOutputPrice());
        } catch (Exception e) {
            log.warn("读取模型单价失败: modelId={}, {}", modelId, e.toString());
            return null;
        }
    }
}
