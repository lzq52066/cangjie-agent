package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.mapper.LongTermMemoryMapper;
import cn.cangjiecloud.prompt.service.ILongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional(rollbackFor = Exception.class)
    public LongTermMemoryEntity upsert(LongTermMemoryEntity entity) {
        // 按 userId + applicationId + dimension + content 指纹去重
        LambdaQueryWrapper<LongTermMemoryEntity> wrapper = new LambdaQueryWrapper<LongTermMemoryEntity>()
                .eq(LongTermMemoryEntity::getUserId, entity.getUserId())
                .eq(LongTermMemoryEntity::getApplicationId, entity.getApplicationId())
                .eq(LongTermMemoryEntity::getDimension, entity.getDimension())
                .eq(LongTermMemoryEntity::getContent, entity.getContent())
                .eq(LongTermMemoryEntity::getDeleted, 0);
        LongTermMemoryEntity existing = getOne(wrapper);
        if (existing != null) {
            // 更新置信度（取最大值）和来源
            existing.setConfidence(Math.max(
                    entity.getConfidence() != null ? entity.getConfidence() : 0.8,
                    existing.getConfidence() != null ? existing.getConfidence() : 0.8));
            if (StringUtils.hasText(entity.getSource())) {
                existing.setSource(entity.getSource());
            }
            existing.setUpdateTime(LocalDateTime.now());
            updateById(existing);
            log.debug("长期记忆已更新: userId={}, dimension={}, content={}",
                    entity.getUserId(), entity.getDimension(), entity.getContent());
            return existing;
        }
        // 新增
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
        log.info("长期记忆已新增: userId={}, dimension={}, content={}",
                entity.getUserId(), entity.getDimension(), entity.getContent());
        return entity;
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

    private String resolveOperator() {
        String updateBy = UserContext.getUserId();
        if (!StringUtils.hasText(updateBy)) {
            updateBy = "system";
        }
        return updateBy;
    }
}