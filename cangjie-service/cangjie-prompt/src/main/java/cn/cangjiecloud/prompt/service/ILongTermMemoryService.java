package cn.cangjiecloud.prompt.service;

import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;

import java.util.List;

public interface ILongTermMemoryService {

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
}