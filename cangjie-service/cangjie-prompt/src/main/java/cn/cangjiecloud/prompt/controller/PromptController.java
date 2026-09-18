package cn.cangjiecloud.prompt.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.prompt.entity.CommandEntity;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ICommandService;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import cn.cangjiecloud.prompt.service.IRuleService;
import cn.cangjiecloud.prompt.service.ISkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/prompt")
public class PromptController {

    private final IPromptTemplateService promptTemplateService;
    private final ISkillService skillService;
    private final IRuleService ruleService;
    private final ICommandService commandService;

    // ==================== 提示词模板 CRUD ====================

    @GetMapping("/template")
    public R<PageResult<PromptTemplateEntity>> listTemplate(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(promptTemplateService.pageQuery(keyword, pageNum, pageSize)));
    }

    @PostMapping("/template")
    public R<PromptTemplateEntity> createTemplate(@RequestBody PromptTemplateEntity entity) {
        return R.data(promptTemplateService.create(entity));
    }

    @PutMapping("/template/{id}")
    public R<PromptTemplateEntity> updateTemplate(@PathVariable String id, @RequestBody PromptTemplateEntity entity) {
        return R.data(promptTemplateService.update(id, entity));
    }

    @DeleteMapping("/template/{id}")
    public R<Void> deleteTemplate(@PathVariable String id) {
        promptTemplateService.delete(id);
        return R.ok();
    }

    @GetMapping("/template/{id}")
    public R<PromptTemplateEntity> getTemplate(@PathVariable String id) {
        return R.data(promptTemplateService.getById(id));
    }

    // ==================== Skill 技能 CRUD ====================

    @GetMapping("/skill")
    public R<PageResult<SkillEntity>> listSkill(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(skillService.pageQuery(keyword, pageNum, pageSize)));
    }

    @PostMapping("/skill")
    public R<SkillEntity> createSkill(@RequestBody SkillEntity entity) {
        return R.data(skillService.create(entity));
    }

    @PutMapping("/skill/{id}")
    public R<SkillEntity> updateSkill(@PathVariable String id, @RequestBody SkillEntity entity) {
        return R.data(skillService.update(id, entity));
    }

    @DeleteMapping("/skill/{id}")
    public R<Void> deleteSkill(@PathVariable String id) {
        skillService.delete(id);
        return R.ok();
    }

    @GetMapping("/skill/{id}")
    public R<SkillEntity> getSkill(@PathVariable String id) {
        return R.data(skillService.getById(id));
    }

    // ==================== 规则 CRUD ====================

    @GetMapping("/rule")
    public R<PageResult<RuleEntity>> listRule(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(ruleService.pageQuery(keyword, pageNum, pageSize)));
    }

    @PostMapping("/rule")
    public R<RuleEntity> createRule(@RequestBody RuleEntity entity) {
        return R.data(ruleService.create(entity));
    }

    @PutMapping("/rule/{id}")
    public R<RuleEntity> updateRule(@PathVariable String id, @RequestBody RuleEntity entity) {
        return R.data(ruleService.update(id, entity));
    }

    @DeleteMapping("/rule/{id}")
    public R<Void> deleteRule(@PathVariable String id) {
        ruleService.delete(id);
        return R.ok();
    }

    @GetMapping("/rule/{id}")
    public R<RuleEntity> getRule(@PathVariable String id) {
        return R.data(ruleService.getById(id));
    }

    // ==================== 命令 CRUD ====================

    @GetMapping("/command")
    public R<PageResult<CommandEntity>> listCommand(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.data(PageResult.of(commandService.pageQuery(keyword, pageNum, pageSize)));
    }

    @PostMapping("/command")
    public R<CommandEntity> createCommand(@RequestBody CommandEntity entity) {
        return R.data(commandService.create(entity));
    }

    @PutMapping("/command/{id}")
    public R<CommandEntity> updateCommand(@PathVariable String id, @RequestBody CommandEntity entity) {
        return R.data(commandService.update(id, entity));
    }

    @DeleteMapping("/command/{id}")
    public R<Void> deleteCommand(@PathVariable String id) {
        commandService.delete(id);
        return R.ok();
    }

    @GetMapping("/command/{id}")
    public R<CommandEntity> getCommand(@PathVariable String id) {
        return R.data(commandService.getById(id));
    }
}
