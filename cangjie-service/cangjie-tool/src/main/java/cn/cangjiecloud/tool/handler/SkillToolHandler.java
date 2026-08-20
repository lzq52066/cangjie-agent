package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ISkillService;
import cn.cangjiecloud.tool.annotation.ToolHandlerType;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.executor.GroovyScriptExecutor;
import cn.cangjiecloud.tool.util.ToolNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 技能工具处理器 — 将 Skill 实体化为可 Function Calling 的工具
 * <p>
 * 技能与工具职责不同：
 * - 工具：API 调用 / 脚本执行 / MCP 连接
 * - 技能：领域知识 + 执行逻辑的组合体，通过 Groovy 脚本落地
 * <p>
 * 当 LLM 调用以 skill_ 开头的 function 时，本 handler 负责：
 * 1. 解析 skillId
 * 2. 加载 SkillEntity
 * 3. 使用 Groovy 执行内容（支持参数注入）
 */
@Slf4j
@Component
@ToolHandlerType(ToolConstants.ToolType.SKILL)
@RequiredArgsConstructor
public class SkillToolHandler extends AbsToolHandler {

    private final ISkillService skillService;

    @Override
    public ToolSpecification buildToolSpecification(ToolEntity entity) {
        // 从 tool entity 关联的 config 中获取 skillId
        String skillId = entity.getConfig();
        if (!StringUtils.hasText(skillId)) {
            return ToolSpecification.builder()
                    .toolId(entity.getId())
                    .name(ToolNaming.buildToolName(entity.getId()))
                    .description(entity.getDescription())
                    .toolType(ToolConstants.ToolType.SKILL)
                    .parameters(Map.of("type", "object", "properties", Map.of()))
                    .build();
        }
        return buildToolSpecification(skillId);
    }

    /**
     * 直接从 SkillEntity 构建 ToolSpecification
     */
    public ToolSpecification buildToolSpecification(String skillId) {
        SkillEntity skill = skillService.getById(skillId);
        if (skill == null) {
            return null;
        }
        return ToolSpecification.builder()
                .toolId(skill.getId())
                .name(ToolNaming.buildSkillName(skill.getId()))
                .description(skill.getDescription() != null ? skill.getDescription() : skill.getName())
                .toolType(ToolConstants.ToolType.SKILL)
                .parameters(parseParameters(skill.getParameters()))
                .build();
    }

    @Override
    public ToolExecuteResultDTO execute(ToolEntity entity, Map<String, Object> params) {
        String skillId = entity.getConfig();
        if (!StringUtils.hasText(skillId)) {
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error("工具未关联技能 ID")
                    .executionTime(0L)
                    .build();
        }
        return execute(skillId, params);
    }

    /**
     * 直接按 skillId 执行
     */
    public ToolExecuteResultDTO execute(String skillId, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        SkillEntity skill = skillService.getById(skillId);
        if (skill == null) {
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error("技能不存在: " + skillId)
                    .executionTime(System.currentTimeMillis() - start)
                    .build();
        }
        if (!"active".equals(skill.getStatus())) {
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error("技能未激活: " + skill.getName())
                    .executionTime(System.currentTimeMillis() - start)
                    .build();
        }
        return executeSkill(skill, params);
    }

    private ToolExecuteResultDTO executeSkill(SkillEntity skill, Map<String, Object> params) {
        long start = System.currentTimeMillis();

        // 类型 1: Groovy 脚本执行（有 content）
        if (StringUtils.hasText(skill.getContent())) {
            return executeGroovy(skill, params, start);
        }

        // 类型 2: 纯知识型技能 — 返回内容作为参考
        String result = skill.getDescription() != null ? skill.getDescription() : skill.getName();
        long cost = System.currentTimeMillis() - start;
        log.info("技能执行完成(知识型): {} ({}), 耗时 {}ms", skill.getName(), skill.getId(), cost);
        return ToolExecuteResultDTO.builder()
                .success(true)
                .output(result)
                .executionTime(cost)
                .build();
    }

    private ToolExecuteResultDTO executeGroovy(SkillEntity skill, Map<String, Object> params, long start) {
        try {
            GroovyScriptExecutor.ScriptResult result =
                    new GroovyScriptExecutor().execute(skill.getContent(), params);

            long cost = System.currentTimeMillis() - start;
            if (result.isSuccess()) {
                log.info("技能执行成功(Groovy): {} ({}), 耗时 {}ms", skill.getName(), skill.getId(), cost);
                return ToolExecuteResultDTO.builder()
                        .success(true)
                        .output(result.result())
                        .executionTime(cost)
                        .build();
            } else {
                log.error("技能执行失败: {} ({}), {}", skill.getName(), skill.getId(), result.error());
                return ToolExecuteResultDTO.builder()
                        .success(false)
                        .error(result.error())
                        .executionTime(cost)
                        .build();
            }
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("技能执行异常: {} ({})", skill.getName(), skill.getId(), e);
            return ToolExecuteResultDTO.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTime(cost)
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        try {
            return com.alibaba.fastjson.JSON.parseObject(parameters);
        } catch (Exception e) {
            return Map.of();
        }
    }
}