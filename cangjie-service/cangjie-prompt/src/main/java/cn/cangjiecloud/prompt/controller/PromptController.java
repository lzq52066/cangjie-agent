package cn.cangjiecloud.prompt.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.prompt.entity.CommandEntity;
import cn.cangjiecloud.prompt.entity.MemoryEntity;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ICommandService;
import cn.cangjiecloud.prompt.service.IMemoryService;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import cn.cangjiecloud.prompt.service.IRuleService;
import cn.cangjiecloud.prompt.service.ISkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/prompt")
public class PromptController {

    private final IPromptTemplateService promptTemplateService;
    private final ISkillService skillService;
    private final IMemoryService memoryService;
    private final IRuleService ruleService;
    private final ICommandService commandService;

    // ==================== 提示词模板 CRUD ====================

    @GetMapping("/template")
    public R<List<PromptTemplateEntity>> listTemplate(@RequestParam(required = false) String keyword) {
        return R.data(promptTemplateService.list(keyword));
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
    public R<List<SkillEntity>> listSkill(@RequestParam(required = false) String keyword) {
        return R.data(skillService.list(keyword));
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

    // ==================== 记忆 CRUD ====================

    @GetMapping("/memory")
    public R<List<MemoryEntity>> listMemory(@RequestParam(required = false) String keyword) {
        return R.data(memoryService.list(keyword));
    }

    @PostMapping("/memory")
    public R<MemoryEntity> createMemory(@RequestBody MemoryEntity entity) {
        return R.data(memoryService.create(entity));
    }

    @PutMapping("/memory/{id}")
    public R<MemoryEntity> updateMemory(@PathVariable String id, @RequestBody MemoryEntity entity) {
        return R.data(memoryService.update(id, entity));
    }

    @DeleteMapping("/memory/{id}")
    public R<Void> deleteMemory(@PathVariable String id) {
        memoryService.delete(id);
        return R.ok();
    }

    @GetMapping("/memory/{id}")
    public R<MemoryEntity> getMemory(@PathVariable String id) {
        return R.data(memoryService.getById(id));
    }

    // ==================== 规则 CRUD ====================

    @GetMapping("/rule")
    public R<List<RuleEntity>> listRule(@RequestParam(required = false) String keyword) {
        return R.data(ruleService.list(keyword));
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
    public R<List<CommandEntity>> listCommand(@RequestParam(required = false) String keyword) {
        return R.data(commandService.list(keyword));
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
