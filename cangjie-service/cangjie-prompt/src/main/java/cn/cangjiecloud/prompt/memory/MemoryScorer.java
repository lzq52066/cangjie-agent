package cn.cangjiecloud.prompt.memory;

import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 记忆评分器：衡量一条记忆的"当前有效强度"，用于选择性注入与遗忘淘汰
 * <p>
 * score = 置信度 × 时近衰减 × 触发强度加成，归一化到 [0, 1]：
 * <ul>
 *   <li>置信度：提取/人工给定的可信度</li>
 *   <li>时近衰减：距最后触发时间按指数衰减（半衰期可配），越久越弱</li>
 *   <li>触发强度：被注入使用次数越多越强（对数增长，封顶 2 倍后归一）</li>
 * </ul>
 */
@Component
public class MemoryScorer {

    /** 时近衰减半衰期（天）：超过该天数未触发的记忆强度衰减到一半 */
    @Value("${cangjie.memory.decay.half-life-days:30}")
    private double halfLifeDays;

    /**
     * 计算记忆强度分（0~1）
     */
    public double score(LongTermMemoryEntity memory) {
        double confidence = memory.getConfidence() != null ? memory.getConfidence() : 0.8;
        double recency = recencyWeight(memory.getLastTriggeredAt());
        double triggerBoost = triggerBoost(memory.getTriggerCount());
        return Math.max(0, Math.min(1, confidence * recency * triggerBoost));
    }

    /**
     * 时近衰减权重：exp(-days / halfLife)
     */
    private double recencyWeight(LocalDateTime lastTriggeredAt) {
        if (lastTriggeredAt == null) {
            return 0.5;
        }
        long days = ChronoUnit.DAYS.between(lastTriggeredAt, LocalDateTime.now());
        double halfLife = Math.max(1, halfLifeDays);
        return Math.exp(-Math.max(0, days) / halfLife);
    }

    /**
     * 触发强度加成：1 + log10(1 + 触发次数)，封顶 2 倍后归一到 [0.5, 1]
     */
    private double triggerBoost(Integer triggerCount) {
        int triggers = triggerCount != null ? Math.max(0, triggerCount) : 0;
        double boost = Math.min(1 + Math.log10(1 + triggers), 2.0);
        return boost / 2.0;
    }
}
