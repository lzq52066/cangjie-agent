package cn.cangjiecloud.application.service.impl;

import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        ObjectNode bundle = JsonUtils.newObject();
        ObjectNode appSnapshot = JsonUtils.mapper().valueToTree(app);
        SNAPSHOT_EXCLUDES.forEach(appSnapshot::remove);
        bundle.set("application", appSnapshot);

        if (StringUtils.hasText(app.getPromptTemplateId())) {
            PromptTemplateEntity pt = promptTemplateService.getById(app.getPromptTemplateId());
            if (pt != null) {
                ObjectNode ptJson = JsonUtils.newObject();
                ptJson.put("name", pt.getName());
                ptJson.put("content", pt.getContent());
                ptJson.put("category", pt.getCategory());
                ptJson.put("description", pt.getDescription());
                ptJson.put("variables", pt.getVariables());
                bundle.set("promptTemplate", ptJson);
            }
        }

        WorkflowEntity wf = workflowService.getByApplicationId(applicationId);
        if (wf != null) {
            bundle.set("workflow", workflowToJson(wf));
        }

        ApplicationTemplateEntity template = new ApplicationTemplateEntity();
        template.setName(StringUtils.hasText(name) ? name : app.getName() + " 模板");
        template.setDescription(StringUtils.hasText(description) ? description : app.getDescription());
        template.setIcon(app.getIcon());
        template.setCategory(category);
        template.setAppType(app.getType());
        template.setSnapshot(bundle.toString());
        template.setUseCount(0);
        template.setStatus("published");
        template.setVersion(1);
        template.setBuiltin(false);
        save(template);
        log.info("Bundle 模板已创建: {} <- 应用 {} (含提示词={}, 工作流={})",
                template.getName(), app.getName(),
                bundle.has("promptTemplate"), bundle.has("workflow"));
        return template;
    }

    @Override
    public IPage<ApplicationTemplateEntity> pageTemplates(String category, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ApplicationTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        // 列表不返回 bundle 大字段
        wrapper.select(ApplicationTemplateEntity.class,
                f -> !"snapshot".equals(f.getColumn()));
        wrapper.eq(ApplicationTemplateEntity::getStatus, "published");
        if (StringUtils.hasText(category)) {
            wrapper.eq(ApplicationTemplateEntity::getCategory, category);
        }
        wrapper.orderByDesc(ApplicationTemplateEntity::getBuiltin)
                .orderByDesc(ApplicationTemplateEntity::getUseCount)
                .orderByDesc(ApplicationTemplateEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
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

        ObjectNode bundle;
        try {
            bundle = JsonUtils.parseObject(template.getSnapshot());
        } catch (Exception e) {
            throw new ApiException("模板快照损坏: " + e.getMessage());
        }

        // 1. 级联创建提示词模板
        String newPromptId = null;
        ObjectNode pt = objectField(bundle, "promptTemplate");
        if (pt != null && StringUtils.hasText(textOrNull(pt, "content"))) {
            PromptTemplateEntity ptEntity = new PromptTemplateEntity();
            String ptName = textOrNull(pt, "name");
            ptEntity.setName(StringUtils.hasText(ptName) ? ptName : template.getName() + " 提示词");
            ptEntity.setContent(textOrNull(pt, "content"));
            ptEntity.setCategory(textOrNull(pt, "category"));
            ptEntity.setDescription(textOrNull(pt, "description"));
            ptEntity.setVariables(textOrNull(pt, "variables"));
            ptEntity.setStatus("active");
            ptEntity.setIsDefault(false);
            newPromptId = promptTemplateService.create(ptEntity).getId();
        }

        // 2. 创建应用（bundle.application 为新格式；根对象为旧格式快照，均兼容）
        ObjectNode bundleApp = objectField(bundle, "application");
        JsonNode appSnapshot = bundleApp != null ? bundleApp : bundle;
        ApplicationEntity app = new ApplicationEntity();
        app.setName(StringUtils.hasText(appName) ? appName : template.getName());
        app.setDescription(textOrNull(appSnapshot, "description"));
        String snapshotType = textOrNull(appSnapshot, "type");
        app.setType(StringUtils.hasText(snapshotType) ? snapshotType : template.getAppType());
        app.setModelId(textOrNull(appSnapshot, "modelId"));
        app.setKnowledgeBaseIds(textOrNull(appSnapshot, "knowledgeBaseIds"));
        app.setSkillIds(textOrNull(appSnapshot, "skillIds"));
        app.setRuleIds(textOrNull(appSnapshot, "ruleIds"));
        app.setToolIds(textOrNull(appSnapshot, "toolIds"));
        app.setMemoryEnabled(appSnapshot.hasNonNull("memoryEnabled")
                ? appSnapshot.get("memoryEnabled").asBoolean() : null);
        app.setMaxTurns(appSnapshot.hasNonNull("maxTurns")
                ? appSnapshot.get("maxTurns").asInt() : null);
        app.setTemperature(appSnapshot.hasNonNull("temperature")
                ? appSnapshot.get("temperature").asDouble() : null);
        app.setConfig(textOrNull(appSnapshot, "config"));
        app.setSuggestions(textOrNull(appSnapshot, "suggestions"));
        app.setIcon(textOrNull(appSnapshot, "icon"));
        app.setRagMode(textOrNull(appSnapshot, "ragMode"));
        app.setStatus("draft");
        app.setTokenQuota(0L);
        if (newPromptId != null) {
            app.setPromptTemplateId(newPromptId);
        }
        applicationService.save(app);

        // 3. 级联创建工作流（直接发布，保证创建即可运行）
        ObjectNode wf = objectField(bundle, "workflow");
        if (wf != null && StringUtils.hasText(textOrNull(wf, "nodes"))) {
            WorkflowEntity wfEntity = new WorkflowEntity();
            String wfName = textOrNull(wf, "name");
            wfEntity.setName(StringUtils.hasText(wfName) ? wfName : template.getName() + " 工作流");
            wfEntity.setDescription(textOrNull(wf, "description"));
            wfEntity.setNodes(textOrNull(wf, "nodes"));
            String edges = textOrNull(wf, "edges");
            wfEntity.setEdges(StringUtils.hasText(edges) ? edges : "[]");
            String variables = textOrNull(wf, "variables");
            wfEntity.setVariables(StringUtils.hasText(variables) ? variables : "[]");
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
        ObjectNode bundle;
        try {
            bundle = JsonUtils.parseObject(bundleJson);
        } catch (Exception e) {
            throw new ApiException("模板 JSON 解析失败: " + e.getMessage());
        }
        if (bundle == null || !StringUtils.hasText(textOrNull(bundle, "name"))) {
            throw new ApiException("模板缺少 name 字段");
        }
        String templateKey = textOrNull(bundle, "templateKey");

        // 按 templateKey 幂等 upsert
        ApplicationTemplateEntity existing = StringUtils.hasText(templateKey)
                ? getOne(new LambdaQueryWrapper<ApplicationTemplateEntity>()
                        .eq(ApplicationTemplateEntity::getTemplateKey, templateKey)
                        .last("LIMIT 1"))
                : null;

        ApplicationTemplateEntity target = existing != null ? existing : new ApplicationTemplateEntity();
        target.setTemplateKey(templateKey);
        target.setName(textOrNull(bundle, "name"));
        target.setDescription(textOrNull(bundle, "description"));
        target.setIcon(textOrNull(bundle, "icon"));
        target.setCategory(textOrNull(bundle, "category"));
        String appType = textOrNull(bundle, "appType");
        target.setAppType(StringUtils.hasText(appType) ? appType : "chat");
        target.setVersion(bundle.hasNonNull("version") ? bundle.get("version").asInt() : 1);
        target.setBuiltin(builtin || bundle.path("builtin").asBoolean(false));
        String status = textOrNull(bundle, "status");
        target.setStatus(StringUtils.hasText(status) ? status : "published");
        target.setSnapshot(bundle.toString());

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
        ObjectNode bundle;
        try {
            bundle = JsonUtils.parseObject(template.getSnapshot());
        } catch (Exception e) {
            bundle = JsonUtils.newObject();
        }
        // 元信息合并到顶层，保证导出文件自包含
        bundle.put("templateKey", template.getTemplateKey());
        bundle.put("name", template.getName());
        bundle.put("description", template.getDescription());
        bundle.put("icon", template.getIcon());
        bundle.put("category", template.getCategory());
        bundle.put("appType", template.getAppType());
        bundle.put("version", template.getVersion());
        return bundle.toString();
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

    private ObjectNode workflowToJson(WorkflowEntity wf) {
        ObjectNode json = JsonUtils.newObject();
        json.put("name", wf.getName());
        json.put("description", wf.getDescription());
        json.put("nodes", StringUtils.hasText(wf.getNodes()) ? wf.getNodes() : "[]");
        json.put("edges", StringUtils.hasText(wf.getEdges()) ? wf.getEdges() : "[]");
        json.put("variables", StringUtils.hasText(wf.getVariables()) ? wf.getVariables() : "[]");
        return json;
    }

    /** 取出对象字段：不存在、为 null 或不是 JSON 对象时返回 null */
    private static ObjectNode objectField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value instanceof ObjectNode object ? object : null;
    }

    /** 取出文本字段：不存在或为 null 时返回 null（数值等其它类型取字面量） */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
