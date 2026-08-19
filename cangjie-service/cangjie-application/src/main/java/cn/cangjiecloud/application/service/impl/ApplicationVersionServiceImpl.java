package cn.cangjiecloud.application.service.impl;

import cn.cangjiecloud.application.api.dto.ApplicationRollbackDTO;
import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationVersionEntity;
import cn.cangjiecloud.application.mapper.ApplicationMapper;
import cn.cangjiecloud.application.mapper.ApplicationVersionMapper;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.application.service.IApplicationVersionService;
import cn.cangjiecloud.common.exception.ApiException;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApplicationVersionServiceImpl extends ServiceImpl<ApplicationVersionMapper, ApplicationVersionEntity>
        implements IApplicationVersionService {

    private final IApplicationService applicationService;
    private final ApplicationMapper applicationMapper;

    public ApplicationVersionServiceImpl(IApplicationService applicationService, ApplicationMapper applicationMapper) {
        this.applicationService = applicationService;
        this.applicationMapper = applicationMapper;
    }

    @Override
    public ApplicationVersionDTO getVersion(String versionId) {
        ApplicationVersionEntity entity = getById(versionId);
        return entity == null ? null : toDTO(entity);
    }

    @Override
    public List<ApplicationVersionDTO> listByApplication(String applicationId) {
        LambdaQueryWrapper<ApplicationVersionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationVersionEntity::getApplicationId, applicationId)
                .orderByDesc(ApplicationVersionEntity::getVersion);
        List<ApplicationVersionEntity> list = list(wrapper);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVersionDTO publish(String applicationId, String operator) {
        ApplicationEntity entity = applicationService.getById(applicationId);
        if (entity == null) {
            throw new ApiException("应用不存在");
        }

        int maxVersion = getMaxVersion(applicationId);
        ApplicationVersionEntity version = new ApplicationVersionEntity();
        version.setApplicationId(applicationId);
        version.setVersion(maxVersion + 1);
        version.setName(entity.getName());
        version.setDescription(entity.getDescription());
        version.setType(entity.getType());
        version.setModelId(entity.getModelId());
        version.setKnowledgeBaseIds(entity.getKnowledgeBaseIds());
        version.setPromptTemplateId(entity.getPromptTemplateId());
        version.setSkillIds(entity.getSkillIds());
        version.setRuleIds(entity.getRuleIds());
        version.setMemoryEnabled(entity.getMemoryEnabled());
        version.setMaxTurns(entity.getMaxTurns());
        version.setTemperature(entity.getTemperature());
        version.setConfig(entity.getConfig());
        version.setSuggestions(entity.getSuggestions());
        version.setIcon(entity.getIcon());
        version.setSnapshot(JSON.toJSONString(entity));
        version.setPublishLog("发布版本 v" + (maxVersion + 1));
        version.setPublishBy(operator != null ? operator : "system");
        save(version);

        log.info("应用版本已发布: app={}, version={}", applicationId, version.getVersion());
        return toDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(String applicationId, String versionId) {
        ApplicationVersionEntity targetVersion = getById(versionId);
        if (targetVersion == null) {
            throw new ApiException("目标版本不存在");
        }
        if (!applicationId.equals(targetVersion.getApplicationId())) {
            throw new ApiException("版本与应用不匹配");
        }

        ApplicationEntity current = applicationService.getById(applicationId);
        if (current == null) {
            throw new ApiException("应用不存在");
        }

        // 从快照恢复字段（保持 ID、createTime 等不变）
        ApplicationEntity snapshot = JSON.parseObject(targetVersion.getSnapshot(), ApplicationEntity.class);
        current.setName(snapshot.getName());
        current.setDescription(snapshot.getDescription());
        current.setType(snapshot.getType());
        current.setModelId(snapshot.getModelId());
        current.setKnowledgeBaseIds(snapshot.getKnowledgeBaseIds());
        current.setPromptTemplateId(snapshot.getPromptTemplateId());
        current.setSkillIds(snapshot.getSkillIds());
        current.setRuleIds(snapshot.getRuleIds());
        current.setMemoryEnabled(snapshot.getMemoryEnabled());
        current.setMaxTurns(snapshot.getMaxTurns());
        current.setTemperature(snapshot.getTemperature());
        current.setConfig(snapshot.getConfig());
        current.setSuggestions(snapshot.getSuggestions());
        current.setIcon(snapshot.getIcon());
        applicationMapper.updateById(current);

        log.info("应用已回滚: app={}, version={}", applicationId, targetVersion.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String versionId) {
        ApplicationVersionEntity version = getById(versionId);
        if (version == null) {
            throw new ApiException("版本不存在");
        }
        int maxVersion = getMaxVersion(version.getApplicationId());
        if (version.getVersion() == maxVersion) {
            throw new ApiException("无法删除最新版本");
        }
        removeById(versionId);
        log.info("应用版本已删除: versionId={}", versionId);
    }

    private int getMaxVersion(String applicationId) {
        LambdaQueryWrapper<ApplicationVersionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationVersionEntity::getApplicationId, applicationId);
        ApplicationVersionEntity latest = getOne(wrapper.last("ORDER BY version DESC LIMIT 1"));
        return latest != null ? latest.getVersion() : 0;
    }

    private ApplicationVersionDTO toDTO(ApplicationVersionEntity v) {
        ApplicationVersionDTO dto = new ApplicationVersionDTO();
        BeanUtils.copyProperties(v, dto);
        return dto;
    }
}