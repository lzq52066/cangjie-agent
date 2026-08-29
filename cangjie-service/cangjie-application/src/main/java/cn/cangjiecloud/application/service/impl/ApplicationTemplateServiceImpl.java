package cn.cangjiecloud.application.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.mapper.ApplicationTemplateMapper;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.application.service.IApplicationTemplateService;
import cn.cangjiecloud.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTemplateServiceImpl
        extends ServiceImpl<ApplicationTemplateMapper, ApplicationTemplateEntity>
        implements IApplicationTemplateService {

    /** 快照中需要剔除的实例相关字段（ID、密钥、统计、审计字段） */
    private static final List<String> SNAPSHOT_EXCLUDES = List.of(
            "id", "apikey", "status", "tokensUsed", "tokenQuota",
            "createBy", "updateBy", "createTime", "updateTime", "deleted");

    private final IApplicationService applicationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationTemplateEntity saveFromApplication(String applicationId, String name,
                                                         String description, String category) {
        ApplicationEntity app = applicationService.getById(applicationId);
        if (app == null) {
            throw new ApiException("应用不存在");
        }

        JSONObject snapshot = (JSONObject) JSON.toJSON(app);
        SNAPSHOT_EXCLUDES.forEach(snapshot::remove);

        ApplicationTemplateEntity template = new ApplicationTemplateEntity();
        template.setName(StringUtils.hasText(name) ? name : app.getName() + " 模板");
        template.setDescription(StringUtils.hasText(description) ? description : app.getDescription());
        template.setIcon(app.getIcon());
        template.setCategory(category);
        template.setAppType(app.getType());
        template.setSnapshot(snapshot.toJSONString());
        template.setUseCount(0);
        template.setStatus("published");
        save(template);
        log.info("应用模板已创建: {} <- 应用 {}", template.getName(), app.getName());
        return template;
    }

    @Override
    public List<ApplicationTemplateEntity> listTemplates(String category) {
        LambdaQueryWrapper<ApplicationTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationTemplateEntity::getStatus, "published")
                .orderByDesc(ApplicationTemplateEntity::getUseCount)
                .orderByDesc(ApplicationTemplateEntity::getCreateTime);
        if (StringUtils.hasText(category)) {
            wrapper.eq(ApplicationTemplateEntity::getCategory, category);
        }
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationEntity createFromTemplate(String templateId, String appName) {
        ApplicationTemplateEntity template = getById(templateId);
        if (template == null) {
            throw new ApiException("模板不存在");
        }
        if (!"published".equals(template.getStatus())) {
            throw new ApiException("模板已下线，无法使用");
        }

        JSONObject snapshot;
        try {
            snapshot = JSON.parseObject(template.getSnapshot());
        } catch (Exception e) {
            throw new ApiException("模板快照损坏: " + e.getMessage());
        }

        ApplicationEntity app = new ApplicationEntity();
        app.setName(StringUtils.hasText(appName) ? appName : template.getName());
        app.setDescription(snapshot.getString("description"));
        app.setType(StringUtils.hasText(snapshot.getString("type")) ? snapshot.getString("type") : template.getAppType());
        app.setModelId(snapshot.getString("modelId"));
        app.setKnowledgeBaseIds(snapshot.getString("knowledgeBaseIds"));
        app.setPromptTemplateId(snapshot.getString("promptTemplateId"));
        app.setSkillIds(snapshot.getString("skillIds"));
        app.setRuleIds(snapshot.getString("ruleIds"));
        app.setToolIds(snapshot.getString("toolIds"));
        app.setMemoryEnabled(snapshot.getBoolean("memoryEnabled"));
        app.setMaxTurns(snapshot.getInteger("maxTurns"));
        app.setTemperature(snapshot.getDouble("temperature"));
        app.setConfig(snapshot.getString("config"));
        app.setSuggestions(snapshot.getString("suggestions"));
        app.setIcon(snapshot.getString("icon"));
        app.setRagMode(snapshot.getString("ragMode"));
        app.setStatus("draft");
        app.setTokenQuota(0L);
        applicationService.save(app);

        template.setUseCount(template.getUseCount() != null ? template.getUseCount() + 1 : 1);
        updateById(template);
        log.info("已从模板创建应用: {} <- 模板 {}", app.getName(), template.getName());
        return app;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(String id) {
        ApplicationTemplateEntity template = getById(id);
        if (template == null) {
            return;
        }
        removeById(id);
        log.info("应用模板已删除: {}", template.getName());
    }
}
