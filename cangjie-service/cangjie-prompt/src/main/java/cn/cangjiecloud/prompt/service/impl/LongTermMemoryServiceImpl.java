package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;
import cn.cangjiecloud.prompt.mapper.LongTermMemoryMapper;
import cn.cangjiecloud.prompt.memory.MemoryDedupProperties;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LongTermMemoryServiceImpl
        extends ServiceImpl<LongTermMemoryMapper, LongTermMemoryEntity>
        implements ILongTermMemoryService {

    private final MemoryDedupProperties dedupProperties;

    /** 向量能力可选注入：知识库模块提供 EmbeddingProvider Bean，未配置/缺失时记忆功能降级为精确去重 */
    private final EmbeddingProvider embeddingProvider;

    public LongTermMemoryServiceImpl(MemoryDedupProperties dedupProperties,
                                     ObjectProvider<EmbeddingProvider> embeddingProviderProvider) {
        this.dedupProperties = dedupProperties;
        this.embeddingProvider = embeddingProviderProvider.getIfAvailable();
    }

    @Override
    public List<LongTermMemoryEntity> findActive(String userId, String applicationId, String dimension) {
        return lambdaQuery()
                .eq(LongTermMemoryEntity::getUserId, userId)
                .eq(LongTermMemoryEntity::getApplicationId, applicationId)
                .eq(StringUtils.hasText(dimension), LongTermMemoryEntity::getDimension, dimension)
                .eq(LongTermMemoryEntity::getIsActive, true)
                .list();
    }

    @Override
    public List<LongTermMemoryEntity> findActiveAll(String userId, String applicationId) {
        return lambdaQuery()
                .eq(LongTermMemoryEntity::getUserId, userId)
                .eq(LongTermMemoryEntity::getApplicationId, applicationId)
                .eq(LongTermMemoryEntity::getIsActive, true)
                .orderByAsc(LongTermMemoryEntity::getDimension)
                .list();
    }

    @Override
    public float[] embed(String content) {
        if (embeddingProvider == null || !StringUtils.hasText(content)) {
            return null;
        }
        try {
            return embeddingProvider.embed(content);
        } catch (Exception e) {
            // 向量服务异常不应阻断记忆写入，降级为仅精确去重
            log.warn("记忆向量生成失败，降级为精确去重: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<MemorySimilarity> findSimilar(float[] embedding, LongTermMemoryEntity probe, int limit) {
        if (embedding == null || embedding.length == 0 || probe == null) {
            return List.of();
        }
        return baseMapper.searchSimilar(toVectorLiteral(embedding), embedding.length,
                probe.getUserId(), probe.getApplicationId(),
                StringUtils.hasText(probe.getMemoryType()) ? probe.getMemoryType() : "user",
                probe.getDimension(), probe.getSessionId(),
                limit > 0 ? limit : dedupProperties.getCandidateLimit());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongTermMemoryEntity insertNew(LongTermMemoryEntity entity, float[] embedding) {
        if (entity.getConfidence() == null) {
            entity.setConfidence(0.8);
        }
        if (!StringUtils.hasText(entity.getSource())) {
            entity.setSource("inferred");
        }
        if (entity.getTriggerCount() == null) {
            entity.setTriggerCount(0);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        save(entity);
        if (embedding != null && embedding.length > 0) {
            baseMapper.updateEmbedding(entity.getId(), toVectorLiteral(embedding), embedding.length);
        }
        log.info("长期记忆已新增: userId={}, dimension={}, content={}",
                entity.getUserId(), entity.getDimension(), entity.getContent());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongTermMemoryEntity reinforce(String id, String source) {
        LongTermMemoryEntity existing = getById(id);
        if (existing == null) {
            return null;
        }
        double base = existing.getConfidence() != null ? existing.getConfidence() : 0.8;
        existing.setConfidence(Math.min(1.0, base + dedupProperties.getReinforceStep()));
        // 同一条记忆被人工确认过，则来源升级为人工（explicit 同时受自动遗忘保护）
        if ("explicit".equals(source)) {
            existing.setSource("explicit");
        }
        existing.setUpdateTime(LocalDateTime.now());
        updateById(existing);
        log.debug("长期记忆重复命中，已强化: id={}, confidence={}", id, existing.getConfidence());
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongTermMemoryEntity mergeInto(String targetId, String mergedContent,
                                          double incomingConfidence, float[] embedding) {
        LongTermMemoryEntity target = getById(targetId);
        if (target == null) {
            return null;
        }
        double base = target.getConfidence() != null ? target.getConfidence() : 0.8;
        // 合并保留较高置信度并小幅强化（两条信息互相印证）
        target.setConfidence(Math.min(1.0, Math.max(base, incomingConfidence)
                + dedupProperties.getReinforceStep()));
        target.setContent(mergedContent);
        target.setUpdateTime(LocalDateTime.now());
        updateById(target);
        if (embedding != null && embedding.length > 0) {
            baseMapper.updateEmbedding(targetId, toVectorLiteral(embedding), embedding.length);
        }
        log.info("长期记忆已合并: id={}, content={}", targetId, mergedContent);
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editMemory(String id, String content, Double confidence, String dimension) {
        LongTermMemoryEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        boolean contentChanged = false;
        if (StringUtils.hasText(content) && !content.equals(entity.getContent())) {
            entity.setContent(content);
            contentChanged = true;
        }
        if (confidence != null) {
            entity.setConfidence(confidence);
        }
        if (StringUtils.hasText(dimension)) {
            entity.setDimension(dimension);
        }
        entity.setUpdateTime(LocalDateTime.now());
        updateById(entity);
        // 内容变更后旧向量失效，按新内容重算
        if (contentChanged) {
            float[] vector = embed(content);
            if (vector != null && vector.length > 0) {
                baseMapper.updateEmbedding(id, toVectorLiteral(vector), vector.length);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongTermMemoryEntity upsert(LongTermMemoryEntity entity) {
        // 1. 精确去重：userId + applicationId + dimension + content 完全一致，直接强化
        LambdaQueryWrapper<LongTermMemoryEntity> wrapper = new LambdaQueryWrapper<LongTermMemoryEntity>()
                .eq(LongTermMemoryEntity::getUserId, entity.getUserId())
                .eq(LongTermMemoryEntity::getApplicationId, entity.getApplicationId())
                .eq(LongTermMemoryEntity::getDimension, entity.getDimension())
                .eq(LongTermMemoryEntity::getContent, entity.getContent())
                .eq(LongTermMemoryEntity::getDeleted, 0);
        LongTermMemoryEntity exact = getOne(wrapper);
        if (exact != null) {
            return reinforce(exact.getId(), entity.getSource());
        }

        // 2. 语义去重：同用户+应用+类型下向量近邻，高相似视为同一条，强化而非新增
        float[] vector = embed(entity.getContent());
        if (vector != null) {
            List<MemorySimilarity> neighbors = findSimilar(vector, entity, dedupProperties.getCandidateLimit());
            for (MemorySimilarity neighbor : neighbors) {
                if (neighbor.getSimilarity() != null
                        && neighbor.getSimilarity() >= dedupProperties.getDuplicateThreshold()) {
                    return reinforce(neighbor.getId(), entity.getSource());
                }
            }
        }

        // 3. 无重复，新增（中等相似的"可能合并"由自动提取链路调 LLM 判定，手工录入不调 LLM）
        return insertNew(entity, vector);
    }

    @Override
    public void incrementTrigger(String id) {
        LongTermMemoryEntity entity = getById(id);
        if (entity == null) {
            return;
        }
        int count = entity.getTriggerCount() != null ? entity.getTriggerCount() : 0;
        lambdaUpdate()
                .eq(LongTermMemoryEntity::getId, id)
                .set(LongTermMemoryEntity::getTriggerCount, count + 1)
                .set(LongTermMemoryEntity::getLastTriggeredAt, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivate(String id) {
        String updateBy = resolveOperator();
        lambdaUpdate()
                .eq(LongTermMemoryEntity::getId, id)
                .set(LongTermMemoryEntity::getIsActive, false)
                .set(LongTermMemoryEntity::getUpdateBy, updateBy)
                .set(LongTermMemoryEntity::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("长期记忆已停用: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivateBatch(java.util.Collection<String> ids, String updateBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        lambdaUpdate()
                .in(LongTermMemoryEntity::getId, ids)
                .set(LongTermMemoryEntity::getIsActive, false)
                .set(StringUtils.hasText(updateBy), LongTermMemoryEntity::getUpdateBy, updateBy)
                .set(LongTermMemoryEntity::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("长期记忆批量停用 {} 条", ids.size());
    }

    @Override
    public List<LongTermMemoryEntity> findSceneMemories(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        return lambdaQuery()
                .eq(LongTermMemoryEntity::getSessionId, sessionId)
                .eq(LongTermMemoryEntity::getMemoryType, "scene")
                .eq(LongTermMemoryEntity::getIsActive, true)
                .orderByDesc(LongTermMemoryEntity::getLastTriggeredAt)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reactivate(String id) {
        String updateBy = resolveOperator();
        lambdaUpdate()
                .eq(LongTermMemoryEntity::getId, id)
                .set(LongTermMemoryEntity::getIsActive, true)
                .set(LongTermMemoryEntity::getUpdateBy, updateBy)
                .set(LongTermMemoryEntity::getUpdateTime, LocalDateTime.now())
                .update();
        log.info("长期记忆已重新激活: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMemory(String id) {
        removeById(id);
        log.info("长期记忆已删除: {}", id);
    }

    /** 浮点数组转 pgvector 字面量 "[1.0,0.2,...]" */
    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private String resolveOperator() {
        String updateBy = UserContext.getUserId();
        if (!StringUtils.hasText(updateBy)) {
            updateBy = "system";
        }
        return updateBy;
    }
}
