package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.mapper.SkillMapper;
import cn.cangjiecloud.prompt.service.ISkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class SkillServiceImpl extends ServiceImpl<SkillMapper, SkillEntity>
        implements ISkillService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity create(SkillEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        save(entity);
        log.info("技能已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity update(String id, SkillEntity entity) {
        SkillEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("技能不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SkillEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("技能已删除: {}", id);
    }

    @Override
    public List<SkillEntity> list(String keyword) {
        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SkillEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SkillEntity::getName, keyword)
                    .or().like(SkillEntity::getDescription, keyword);
        }
        return list(wrapper);
    }
}
