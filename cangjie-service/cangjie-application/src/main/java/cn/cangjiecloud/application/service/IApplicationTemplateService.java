package cn.cangjiecloud.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;

import java.util.List;

/**
 * 应用模板服务：从应用沉淀模板、从模板创建应用
 */
public interface IApplicationTemplateService extends IService<ApplicationTemplateEntity> {

    /**
     * 将现有应用保存为模板
     */
    ApplicationTemplateEntity saveFromApplication(String applicationId, String name,
                                                  String description, String category);

    List<ApplicationTemplateEntity> listTemplates(String category);

    /**
     * 从模板创建新应用（草稿态）
     *
     * @param templateId 模板 ID
     * @param appName    新应用名称（为空时使用模板名）
     */
    ApplicationEntity createFromTemplate(String templateId, String appName);

    void deleteTemplate(String id);
}
