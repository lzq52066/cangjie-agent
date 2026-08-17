package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.MemoryEntity;
import cn.cangjiecloud.prompt.mapper.MemoryMapper;
import cn.cangjiecloud.prompt.service.IMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class MemoryServiceImpl extends ServiceImpl<MemoryMapper, MemoryEntity>
        implements IMemoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntity create(MemoryEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        if (entity.getImportance() == null) {
            entity.setImportance(0);
        }
        save(entity);
        log.info("记忆已创建: {} ({})", entity.getRole(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntity update(String id, MemoryEntity entity) {
        MemoryEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("记忆不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        MemoryEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("记忆已删除: {}", id);
    }

    @Override
    public List<MemoryEntity> list(String keyword) {
        LambdaQueryWrapper<MemoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MemoryEntity::getImportance)
                .orderByDesc(MemoryEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(MemoryEntity::getContent, keyword)
                    .or().like(MemoryEntity::getSummary, keyword);
        }
        return list(wrapper);
    }
}
