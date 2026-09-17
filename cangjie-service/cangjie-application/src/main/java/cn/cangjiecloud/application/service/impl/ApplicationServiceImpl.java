package cn.cangjiecloud.application.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.application.api.dto.ApplicationCreateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.mapper.ApplicationMapper;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, ApplicationEntity>
        implements IApplicationService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationEntity create(ApplicationCreateDTO dto) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setType(dto.getType());
        entity.setModelId(dto.getModelId());
        entity.setKnowledgeBaseIds(toJson(dto.getKnowledgeBaseIds()));
        entity.setPromptTemplateId(dto.getPromptTemplateId());
        entity.setSkillIds(toJson(dto.getSkillIds()));
        entity.setRuleIds(toJson(dto.getRuleIds()));
        entity.setToolIds(toJson(dto.getToolIds()));
        entity.setMemoryEnabled(dto.getMemoryEnabled() != null ? dto.getMemoryEnabled() : false);
        entity.setMaxTurns(dto.getMaxTurns() != null ? dto.getMaxTurns() : 20);
        entity.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : 0.7);
        entity.setConfig(dto.getConfig());
        entity.setSuggestions(toJson(dto.getSuggestions()));
        entity.setIcon(dto.getIcon());
        entity.setStatus("draft");
        entity.setTokenQuota(dto.getTokenQuota() != null ? dto.getTokenQuota() : 0L);
        save(entity);
        log.info("应用已创建: {} ({}) type={}", entity.getName(), entity.getId(), entity.getType());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationEntity update(String id, ApplicationCreateDTO dto) {
        ApplicationEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("应用不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getDescription())) entity.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getType())) entity.setType(dto.getType());
        if (StringUtils.hasText(dto.getModelId())) entity.setModelId(dto.getModelId());
        if (dto.getKnowledgeBaseIds() != null) entity.setKnowledgeBaseIds(toJson(dto.getKnowledgeBaseIds()));
        if (StringUtils.hasText(dto.getPromptTemplateId())) entity.setPromptTemplateId(dto.getPromptTemplateId());
        if (dto.getSkillIds() != null) entity.setSkillIds(toJson(dto.getSkillIds()));
        if (dto.getRuleIds() != null) entity.setRuleIds(toJson(dto.getRuleIds()));
        if (dto.getToolIds() != null) entity.setToolIds(toJson(dto.getToolIds()));
        if (dto.getMemoryEnabled() != null) entity.setMemoryEnabled(dto.getMemoryEnabled());
        if (dto.getMaxTurns() != null) entity.setMaxTurns(dto.getMaxTurns());
        if (dto.getTemperature() != null) entity.setTemperature(dto.getTemperature());
        if (StringUtils.hasText(dto.getConfig())) entity.setConfig(dto.getConfig());
        if (dto.getSuggestions() != null) entity.setSuggestions(toJson(dto.getSuggestions()));
        if (StringUtils.hasText(dto.getIcon())) entity.setIcon(dto.getIcon());
        if (dto.getTokenQuota() != null) entity.setTokenQuota(dto.getTokenQuota());
        updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ApplicationEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("应用已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public IPage<ApplicationEntity> pageQuery(String keyword, String type, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ApplicationEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ApplicationEntity::getName, keyword)
                    .or().like(ApplicationEntity::getDescription, keyword));
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(ApplicationEntity::getType, type);
        }
        wrapper.orderByDesc(ApplicationEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationEntity publish(String id) {
        ApplicationEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("应用不存在");
        }
        if (!StringUtils.hasText(entity.getApikey())) {
            entity.setApikey(generateApikey());
        }
        entity.setStatus("published");
        updateById(entity);
        log.info("应用已发布: {} ({}) apikey={}", entity.getName(), id, entity.getApikey());
        return entity;
    }

    @Override
    public ApplicationEntity getByApikey(String apikey) {
        if (!StringUtils.hasText(apikey)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getApikey, apikey)
                .eq(ApplicationEntity::getStatus, "published")
                .last("LIMIT 1"));
    }

    private String generateApikey() {
        return "cj_" + RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 40);
    }

    private String toJson(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return JSON.toJSONString(ids);
    }
}
