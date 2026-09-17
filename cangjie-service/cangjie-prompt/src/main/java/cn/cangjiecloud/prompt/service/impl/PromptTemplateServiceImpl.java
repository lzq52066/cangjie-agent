package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.mapper.PromptTemplateMapper;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplateEntity>
        implements IPromptTemplateService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateEntity create(PromptTemplateEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(false);
        }
        save(entity);
        log.info("提示词模板已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateEntity update(String id, PromptTemplateEntity entity) {
        PromptTemplateEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("提示词模板不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        PromptTemplateEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("提示词模板已删除: {}", id);
    }

    @Override
    public IPage<PromptTemplateEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PromptTemplateEntity::getIsDefault)
                .orderByDesc(PromptTemplateEntity::getCreateTime);
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(PromptTemplateEntity::getName, keyword)
                .or()
                .like(PromptTemplateEntity::getDescription, keyword));
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
