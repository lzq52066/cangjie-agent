package cn.cangjiecloud.prompt.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆遗忘任务（每日执行）
 * <p>
 * 模拟人类遗忘曲线，避免记忆无限膨胀、稀释注入质量：
 * <ol>
 *   <li>低分遗忘：评分低于阈值的记忆停用（软遗忘，可人工恢复）</li>
 *   <li>容量淘汰：单用户单应用激活记忆超过上限时，按评分从低到高停用</li>
 *   <li>保护规则：显式录入（source=explicit）的记忆永不自动遗忘</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayJob {

    /** 显式来源标记：人工录入的记忆不参与自动遗忘 */
    private static final String SOURCE_EXPLICIT = "explicit";

    private final ILongTermMemoryService longTermMemoryService;
    private final MemoryScorer memoryScorer;

    @Value("${cangjie.memory.decay.enabled:true}")
    private boolean enabled;

    /** 评分低于该阈值的记忆将被停用 */
    @Value("${cangjie.memory.decay.threshold:0.25}")
    private double decayThreshold;

    /** 单用户单应用激活记忆容量上限（0 表示不限制） */
    @Value("${cangjie.memory.decay.capacity-per-user:100}")
    private int capacityPerUser;

    @Scheduled(cron = "${cangjie.memory.decay.cron:0 0 3 * * ?}")
    public void runDecay() {
        if (!enabled) {
            return;
        }
        try {
            List<LongTermMemoryEntity> active = longTermMemoryService.list(
                    new LambdaQueryWrapper<LongTermMemoryEntity>()
                            .eq(LongTermMemoryEntity::getIsActive, true));
            if (active.isEmpty()) {
                return;
            }

            int decayed = lowScoreDecay(active);
            int evicted = capacityEviction(active);

            if (decayed > 0 || evicted > 0) {
                log.info("记忆遗忘任务完成: 扫描 {} 条, 低分遗忘 {} 条, 容量淘汰 {} 条",
                        active.size(), decayed, evicted);
            }
        } catch (Exception e) {
            log.error("记忆遗忘任务执行失败", e);
        }
    }

    /**
     * 低分遗忘：评分低于阈值的推断类记忆停用
     */
    private int lowScoreDecay(List<LongTermMemoryEntity> active) {
        int count = 0;
        for (LongTermMemoryEntity memory : active) {
            if (isProtected(memory)) {
                continue;
            }
            double score = memoryScorer.score(memory);
            if (score < decayThreshold) {
                longTermMemoryService.deactivate(memory.getId());
                count++;
                log.debug("记忆低分遗忘: id={}, score={}, content={}",
                        memory.getId(), String.format("%.3f", score), memory.getContent());
            }
        }
        return count;
    }

    /**
     * 容量淘汰：按 用户+应用 分组，超出上限时淘汰评分最低的记忆
     */
    private int capacityEviction(List<LongTermMemoryEntity> active) {
        if (capacityPerUser <= 0) {
            return 0;
        }
        Map<String, List<LongTermMemoryEntity>> groups = new HashMap<>();
        for (LongTermMemoryEntity memory : active) {
            String key = memory.getUserId() + ":" + memory.getApplicationId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(memory);
        }
        int count = 0;
        for (List<LongTermMemoryEntity> group : groups.values()) {
            if (group.size() <= capacityPerUser) {
                continue;
            }
            List<LongTermMemoryEntity> evictable = group.stream()
                    .filter(m -> !isProtected(m))
                    .sorted(Comparator.comparingDouble(memoryScorer::score))
                    .toList();
            int need = group.size() - capacityPerUser;
            for (int i = 0; i < need && i < evictable.size(); i++) {
                longTermMemoryService.deactivate(evictable.get(i).getId());
                count++;
            }
        }
        return count;
    }

    private boolean isProtected(LongTermMemoryEntity memory) {
        return SOURCE_EXPLICIT.equals(memory.getSource());
    }
}
