package cn.cangjiecloud.application.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.application.api.dto.ApplicationTemplateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.service.IApplicationTemplateService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 应用模板：沉淀可复用应用配置，一键创建新应用
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/application-template")
public class ApplicationTemplateController {

    private final IApplicationTemplateService templateService;

    /**
     * 将现有应用保存为模板
     */
    @PostMapping("/from-application")
    public R<ApplicationTemplateEntity> saveFromApplication(@Valid @RequestBody ApplicationTemplateDTO dto) {
        return R.data(templateService.saveFromApplication(
                dto.getApplicationId(), dto.getName(), dto.getDescription(), dto.getCategory()));
    }

    @GetMapping
    public R<List<ApplicationTemplateEntity>> list(@RequestParam(required = false) String category) {
        return R.data(templateService.listTemplates(category));
    }

    /**
     * 从模板创建新应用（草稿态，级联创建提示词模板与工作流）
     */
    @PostMapping("/{id}/create-app")
    public R<ApplicationEntity> createFromTemplate(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        String appName = body != null ? body.get("name") : null;
        return R.data(templateService.createFromTemplate(id, appName));
    }

    /**
     * 导入 Bundle 模板（body 为模板 JSON 原文；带 templateKey 时幂等覆盖）
     */
    @PostMapping("/import")
    public R<ApplicationTemplateEntity> importBundle(@RequestBody String bundleJson) {
        return R.data(templateService.importBundle(bundleJson, false));
    }

    /**
     * 导出 Bundle 模板 JSON（自包含，可跨实例迁移）
     */
    @GetMapping("/{id}/export")
    public R<String> exportBundle(@PathVariable String id) {
        return R.data(templateService.exportBundle(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }
}
