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
     * 从模板创建新应用（草稿态）
     */
    @PostMapping("/{id}/create-app")
    public R<ApplicationEntity> createFromTemplate(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        String appName = body != null ? body.get("name") : null;
        return R.data(templateService.createFromTemplate(id, appName));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }
}
