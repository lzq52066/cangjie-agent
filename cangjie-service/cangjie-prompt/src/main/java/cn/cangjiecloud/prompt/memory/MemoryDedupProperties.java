package cn.cangjiecloud.prompt.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆语义去重 / 合并参数（由 @ConfigurationPropertiesScan 注册）
 */
@Data
@ConfigurationProperties(prefix = "cangjie.memory.dedup")
public class MemoryDedupProperties {

    /** 语义查重时取回的近邻候选条数（阈值过滤在业务层完成） */
    private int candidateLimit = 5;

    /** 相似度 ≥ 该阈值视为同一条记忆：直接强化置信度，不新增 */
    private double duplicateThreshold = 0.92;

    /**
     * 相似度落在 [mergeThreshold, duplicateThreshold) 区间时，
     * 自动提取链路会调 LLM 判断两条记忆是否应归并为一条
     */
    private double mergeThreshold = 0.82;

    /** 重复/合并确认时置信度的强化步长，累加并封顶 1.0 */
    private double reinforceStep = 0.1;
}
