package cn.cangjiecloud.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;

/**
 * 应用模板服务：Bundle 套件模板（应用配置 + 提示词模板 + 工作流）
 * <p>
 * snapshot 存储完整 bundle JSON：
 * { templateKey, name, description, icon, category, appType, version,
 *   application: {...}, promptTemplate: {...}, workflow: {...} }
 * 兼容旧格式（snapshot 直接为应用字段 JSON）。
 */
public interface IApplicationTemplateService extends IService<ApplicationTemplateEntity> {

    /**
     * 将现有应用（含其提示词模板与工作流）保存为 Bundle 模板
     */
    ApplicationTemplateEntity saveFromApplication(String applicationId, String name,
                                                  String description, String category);

    IPage<ApplicationTemplateEntity> pageTemplates(String category, Integer pageNum, Integer pageSize);

    /**
     * 从模板创建新应用：级联创建提示词模板与工作流，并重建绑定
     *
     * @param templateId 模板 ID
     * @param appName    新应用名称（为空时使用模板名）
     */
    ApplicationEntity createFromTemplate(String templateId, String appName);

    /**
     * 导入 Bundle 模板（按 templateKey 幂等 upsert）
     *
     * @param bundleJson 模板 JSON
     * @param builtin    是否标记为官方内置
     */
    ApplicationTemplateEntity importBundle(String bundleJson, boolean builtin);

    /**
     * 导出 Bundle 模板 JSON（自包含，可跨实例迁移）
     */
    String exportBundle(String templateId);

    void deleteTemplate(String id);
}
