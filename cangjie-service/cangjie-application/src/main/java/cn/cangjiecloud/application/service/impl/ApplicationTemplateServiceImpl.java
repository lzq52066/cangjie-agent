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
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Bundle 套件模板服务实现
 * <p>
 * 沉淀：应用配置 + 提示词模板 + 工作流定义打包为一个 bundle；
 * 使用：级联创建新资源并按新 ID 重建绑定，创建出的应用开箱即用。
 */
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
    private final IPromptTemplateService promptTemplateService;
    private final IWorkflowService workflowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationTemplateEntity saveFromApplication(String applicationId, String name,
                                                         String description, String category) {
        ApplicationEntity app = applicationService.getById(applicationId);
        if (app == null) {
            throw new ApiException("应用不存在");
        }

        // 组装 bundle：application + promptTemplate + workflow
        JSONObject bundle = new JSONObject();
        JSONObject appSnapshot = (JSONObject) JSON.toJSON(app);
        SNAPSHOT_EXCLUDES.forEach(appSnapshot::remove);
        bundle.put("application", appSnapshot);

        if (StringUtils.hasText(app.getPromptTemplateId())) {
            PromptTemplateEntity pt = promptTemplateService.getById(app.getPromptTemplateId());
            if (pt != null) {
                JSONObject ptJson = new JSONObject();
                ptJson.put("name", pt.getName());
                ptJson.put("content", pt.getContent());
                ptJson.put("category", pt.getCategory());
                ptJson.put("description", pt.getDescription());
                ptJson.put("variables", pt.getVariables());
                bundle.put("promptTemplate", ptJson);
            }
        }

        WorkflowEntity wf = workflowService.getByApplicationId(applicationId);
        if (wf != null) {
            bundle.put("workflow", workflowToJson(wf));
        }

        ApplicationTemplateEntity template = new ApplicationTemplateEntity();
        template.setName(StringUtils.hasText(name) ? name : app.getName() + " 模板");
        template.setDescription(StringUtils.hasText(description) ? description : app.getDescription());
        template.setIcon(app.getIcon());
        template.setCategory(category);
        template.setAppType(app.getType());
        template.setSnapshot(bundle.toJSONString());
        template.setUseCount(0);
        template.setStatus("published");
        template.setVersion(1);
        template.setBuiltin(false);
        save(template);
        log.info("Bundle 模板已创建: {} <- 应用 {} (含提示词={}, 工作流={})",
                template.getName(), app.getName(),
                bundle.containsKey("promptTemplate"), bundle.containsKey("workflow"));
        return template;
    }

    @Override
    public List<ApplicationTemplateEntity> listTemplates(String category) {
        LambdaQueryWrapper<ApplicationTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        // 列表不返回 bundle 大字段
        wrapper.select(ApplicationTemplateEntity.class,
                f -> !"snapshot".equals(f.getColumn()));
        wrapper.eq(ApplicationTemplateEntity::getStatus, "published")
                .orderByDesc(ApplicationTemplateEntity::getBuiltin)
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

        JSONObject bundle;
        try {
            bundle = JSON.parseObject(template.getSnapshot());
        } catch (Exception e) {
            throw new ApiException("模板快照损坏: " + e.getMessage());
        }

        // 1. 级联创建提示词模板
        String newPromptId = null;
        JSONObject pt = bundle.getJSONObject("promptTemplate");
        if (pt != null && StringUtils.hasText(pt.getString("content"))) {
            PromptTemplateEntity ptEntity = new PromptTemplateEntity();
            ptEntity.setName(StringUtils.hasText(pt.getString("name"))
                    ? pt.getString("name") : template.getName() + " 提示词");
            ptEntity.setContent(pt.getString("content"));
            ptEntity.setCategory(pt.getString("category"));
            ptEntity.setDescription(pt.getString("description"));
            ptEntity.setVariables(pt.getString("variables"));
            ptEntity.setStatus("active");
            ptEntity.setIsDefault(false);
            newPromptId = promptTemplateService.create(ptEntity).getId();
        }

        // 2. 创建应用（bundle.application 为新格式；根对象为旧格式快照，均兼容）
        JSONObject appSnapshot = bundle.getJSONObject("application") != null
                ? bundle.getJSONObject("application") : bundle;
        ApplicationEntity app = new ApplicationEntity();
        app.setName(StringUtils.hasText(appName) ? appName : template.getName());
        app.setDescription(appSnapshot.getString("description"));
        app.setType(StringUtils.hasText(appSnapshot.getString("type"))
                ? appSnapshot.getString("type") : template.getAppType());
        app.setModelId(appSnapshot.getString("modelId"));
        app.setKnowledgeBaseIds(appSnapshot.getString("knowledgeBaseIds"));
        app.setSkillIds(appSnapshot.getString("skillIds"));
        app.setRuleIds(appSnapshot.getString("ruleIds"));
        app.setToolIds(appSnapshot.getString("toolIds"));
        app.setMemoryEnabled(appSnapshot.getBoolean("memoryEnabled"));
        app.setMaxTurns(appSnapshot.getInteger("maxTurns"));
        app.setTemperature(appSnapshot.getDouble("temperature"));
        app.setConfig(appSnapshot.getString("config"));
        app.setSuggestions(appSnapshot.getString("suggestions"));
        app.setIcon(appSnapshot.getString("icon"));
        app.setRagMode(appSnapshot.getString("ragMode"));
        app.setStatus("draft");
        app.setTokenQuota(0L);
        if (newPromptId != null) {
            app.setPromptTemplateId(newPromptId);
        }
        applicationService.save(app);

        // 3. 级联创建工作流（直接发布，保证创建即可运行）
        JSONObject wf = bundle.getJSONObject("workflow");
        if (wf != null && StringUtils.hasText(wf.getString("nodes"))) {
            WorkflowEntity wfEntity = new WorkflowEntity();
            wfEntity.setName(StringUtils.hasText(wf.getString("name"))
                    ? wf.getString("name") : template.getName() + " 工作流");
            wfEntity.setDescription(wf.getString("description"));
            wfEntity.setNodes(wf.getString("nodes"));
            wfEntity.setEdges(StringUtils.hasText(wf.getString("edges")) ? wf.getString("edges") : "[]");
            wfEntity.setVariables(StringUtils.hasText(wf.getString("variables")) ? wf.getString("variables") : "[]");
            wfEntity.setApplicationId(app.getId());
            wfEntity.setStatus("published");
            wfEntity.setVersion(1);
            workflowService.create(wfEntity);
        }

        // 4. 使用计数
        template.setUseCount(template.getUseCount() != null ? template.getUseCount() + 1 : 1);
        updateById(template);
        log.info("已从模板创建应用: {} <- 模板 {} (级联 提示词={}, 工作流={})",
                app.getName(), template.getName(), newPromptId != null, wf != null);
        return app;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationTemplateEntity importBundle(String bundleJson, boolean builtin) {
        JSONObject bundle;
        try {
            bundle = JSON.parseObject(bundleJson);
        } catch (Exception e) {
            throw new ApiException("模板 JSON 解析失败: " + e.getMessage());
        }
        if (bundle == null || !StringUtils.hasText(bundle.getString("name"))) {
            throw new ApiException("模板缺少 name 字段");
        }
        String templateKey = bundle.getString("templateKey");

        // 按 templateKey 幂等 upsert
        ApplicationTemplateEntity existing = StringUtils.hasText(templateKey)
                ? getOne(new LambdaQueryWrapper<ApplicationTemplateEntity>()
                        .eq(ApplicationTemplateEntity::getTemplateKey, templateKey)
                        .last("LIMIT 1"))
                : null;

        ApplicationTemplateEntity target = existing != null ? existing : new ApplicationTemplateEntity();
        target.setTemplateKey(templateKey);
        target.setName(bundle.getString("name"));
        target.setDescription(bundle.getString("description"));
        target.setIcon(bundle.getString("icon"));
        target.setCategory(bundle.getString("category"));
        target.setAppType(StringUtils.hasText(bundle.getString("appType"))
                ? bundle.getString("appType") : "chat");
        Integer version = bundle.getInteger("version");
        target.setVersion(version != null ? version : 1);
        target.setBuiltin(builtin || Boolean.TRUE.equals(bundle.getBoolean("builtin")));
        target.setStatus(StringUtils.hasText(bundle.getString("status"))
                ? bundle.getString("status") : "published");
        target.setSnapshot(bundle.toJSONString());

        if (existing != null) {
            updateById(target);
            log.info("模板已按 templateKey 更新: {}", templateKey);
        } else {
            target.setUseCount(0);
            save(target);
            log.info("模板已导入: {} ({})", target.getName(), templateKey);
        }
        return target;
    }

    @Override
    public String exportBundle(String templateId) {
        ApplicationTemplateEntity template = getById(templateId);
        if (template == null) {
            throw new ApiException("模板不存在");
        }
        JSONObject bundle;
        try {
            bundle = JSON.parseObject(template.getSnapshot());
        } catch (Exception e) {
            bundle = new JSONObject();
        }
        // 元信息合并到顶层，保证导出文件自包含
        bundle.put("templateKey", template.getTemplateKey());
        bundle.put("name", template.getName());
        bundle.put("description", template.getDescription());
        bundle.put("icon", template.getIcon());
        bundle.put("category", template.getCategory());
        bundle.put("appType", template.getAppType());
        bundle.put("version", template.getVersion());
        return bundle.toJSONString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(String id) {
        ApplicationTemplateEntity template = getById(id);
        if (template == null) {
            return;
        }
        if (Boolean.TRUE.equals(template.getBuiltin())) {
            throw new ApiException("官方内置模板不可删除，可在配置中关闭 cangjie.templates.import-builtin");
        }
        removeById(id);
        log.info("应用模板已删除: {}", template.getName());
    }

    private JSONObject workflowToJson(WorkflowEntity wf) {
        JSONObject json = new JSONObject();
        json.put("name", wf.getName());
        json.put("description", wf.getDescription());
        json.put("nodes", StringUtils.hasText(wf.getNodes()) ? wf.getNodes() : "[]");
        json.put("edges", StringUtils.hasText(wf.getEdges()) ? wf.getEdges() : "[]");
        json.put("variables", StringUtils.hasText(wf.getVariables()) ? wf.getVariables() : "[]");
        return json;
    }
}
