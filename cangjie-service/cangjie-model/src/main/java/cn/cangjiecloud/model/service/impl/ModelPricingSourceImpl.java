package cn.cangjiecloud.model.service.impl;

import cn.cangjiecloud.core.model.ModelPricing;
import cn.cangjiecloud.core.model.ModelPricingSource;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.mapper.ModelMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 模型单价来源：从 {@code model_config} 读输入 / 输出单价。
 * <p>
 * 带一层 60 秒的短缓存：这一层在每个模型调用结束时被取一次，直查库里没有错，
 * 但「一次对话 2~5 条调用各多一次 DB 往返」正是链路写入要避免的开销；
 * 代价是改完单价最多一分钟后的调用才按新价算 —— 单价不是分秒必争的量。
 * <p>
 * 过期后也<strong>不在调用线程查库</strong>：先回旧值、把刷新丢到后台单线程。
 * 这一层是被模型回调线程调的，「每 60 秒一次的同步查询」正好落在本次提交想拆掉的那个开销上。
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

    /** 正在后台刷新的模型：同一模型只允许一个刷新任务在途，免得刷新把库打穿 */
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    /** 单价刷新线程：单线程足够，只为把 DB 往返从模型回调线程上摘下来 */
    private final ExecutorService refresher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-pricing-refresher");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdown() {
        refresher.shutdownNow();
    }

    @Override
    public ModelPricing findPricing(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry cached = cache.get(modelId);
        if (cached != null) {
            if (now - cached.loadedAt() >= CACHE_TTL_MS) {
                refreshInBackground(modelId);
            }
            // 过期也先给旧值：成本估算差一分钟的价格无关紧要，差一次回调线程上的 DB 往返是要紧的
            return cached.pricing() == NONE ? null : cached.pricing();
        }
        // 只有这个模型「第一次被用到」才付一次同步查询，此后一律走后台刷新
        ModelPricing pricing = load(modelId);
        cache.put(modelId, new Entry(pricing == null ? NONE : pricing, now));
        return pricing;
    }

    private void refreshInBackground(String modelId) {
        if (!refreshing.add(modelId)) {
            return;
        }
        try {
            refresher.execute(() -> {
                try {
                    ModelPricing pricing = load(modelId);
                    cache.put(modelId, new Entry(pricing == null ? NONE : pricing, System.currentTimeMillis()));
                } catch (Exception e) {
                    log.warn("模型单价后台刷新失败，暂用旧值: modelId={}, {}", modelId, e.toString());
                } finally {
                    refreshing.remove(modelId);
                }
            });
        } catch (RejectedExecutionException e) {
            // 关停中：把在途标记清掉，别让这个模型从此不再刷新
            refreshing.remove(modelId);
        }
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
