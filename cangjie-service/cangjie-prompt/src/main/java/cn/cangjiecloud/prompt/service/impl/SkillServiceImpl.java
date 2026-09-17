package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.mapper.SkillMapper;
import cn.cangjiecloud.prompt.service.ISkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    public IPage<SkillEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SkillEntity::getCreateTime);
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(SkillEntity::getName, keyword)
                .or()
                .like(SkillEntity::getDescription, keyword));
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
