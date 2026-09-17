package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.mapper.RuleMapper;
import cn.cangjiecloud.prompt.service.IRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class RuleServiceImpl extends ServiceImpl<RuleMapper, RuleEntity>
        implements IRuleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleEntity create(RuleEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        if (entity.getPriority() == null) {
            entity.setPriority(0);
        }
        save(entity);
        log.info("规则已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleEntity update(String id, RuleEntity entity) {
        RuleEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("规则不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        RuleEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("规则已删除: {}", id);
    }

    @Override
    public IPage<RuleEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<RuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RuleEntity::getPriority)
                .orderByDesc(RuleEntity::getCreateTime);
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(RuleEntity::getName, keyword)
                .or()
                .like(RuleEntity::getDescription, keyword));
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
