package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;

import java.util.List;

public interface ILongTermMemoryService extends IService<LongTermMemoryEntity> {

    /**
     * 为记忆内容生成向量；向量能力不可用（未配置 Embedding）或调用失败时返回 null（降级，不影响记忆主流程）
     */
    float[] embed(String content);

    /**
     * 向量近邻查询：在同一用户+应用+记忆类型（scene 再限定同一会话）下取语义最相似的激活记忆，
     * embedding 为 null 时返回空列表
     */
    List<MemorySimilarity> findSimilar(float[] embedding, LongTermMemoryEntity probe, int limit);

    /**
     * 新增一条记忆并回写向量（embedding 为 null 时仅入库）
     */
    LongTermMemoryEntity insertNew(LongTermMemoryEntity entity, float[] embedding);

    /**
     * 强化已有记忆：语义/精确重复时递增置信度（封顶 1.0），不改变内容；
     * source 为 explicit 时把来源升级为人工
     */
    LongTermMemoryEntity reinforce(String id, String source);

    /**
     * 合并记忆：用归并后的内容覆盖目标记忆，刷新向量，并按较高置信度小幅强化
     */
    LongTermMemoryEntity mergeInto(String targetId, String mergedContent, double incomingConfidence, float[] embedding);

    /**
     * 手工编辑记忆：可改内容/置信度/维度，内容变更时重算向量
     */
    void editMemory(String id, String content, Double confidence, String dimension);

    /**
     * 按用户 + 应用 + 维度查询激活记忆
     */
    List<LongTermMemoryEntity> findActive(String userId, String applicationId, String dimension);

    /**
     * 按用户 + 应用查询全部激活记忆（不按维度过滤，避免逐维度多次查询）
     */
    List<LongTermMemoryEntity> findActiveAll(String userId, String applicationId);

    /**
     * 新增或更新记忆（按 userId + applicationId + dimension + 内容指纹去重）
     */
    LongTermMemoryEntity upsert(LongTermMemoryEntity entity);

    /**
     * 增加触发次数
     */
    void incrementTrigger(String id);

    /**
     * 软删除
     */
    void deactivate(String id);

    /**
     * 批量软删除（供定时任务使用，避免逐条更新产生 N+1）
     */
    void deactivateBatch(java.util.Collection<String> ids, String updateBy);

    /**
     * 查询会话的场景记忆（激活状态）
     */
    List<LongTermMemoryEntity> findSceneMemories(String sessionId);

    /**
     * 重新激活被遗忘/停用的记忆
     */
    void reactivate(String id);

    /**
     * 彻底删除记忆
     */
    void deleteMemory(String id);
}