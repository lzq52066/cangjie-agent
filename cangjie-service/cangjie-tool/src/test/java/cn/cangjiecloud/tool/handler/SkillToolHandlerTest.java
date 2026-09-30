package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ISkillService;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SkillToolHandler 单元测试
 * <p>
 * 技能工具处理器：将 Skill 实体化为 function calling 工具。
 * 依赖的 ISkillService 使用 mock，Groovy 内容执行真实脚本。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("技能工具处理器 - SkillToolHandler")
class SkillToolHandlerTest {

    @Mock
    private ISkillService skillService;

    private SkillToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SkillToolHandler(skillService);
    }

    /**
     * 构造技能实体
     */
    private SkillEntity skill(String id, String name, String status) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setName(name);
        skill.setStatus(status);
        return skill;
    }

    /**
     * 构造关联了 skillId 的工具实体
     */
    private ToolEntity entity(String id, String skillId) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setConfig(skillId);
        return entity;
    }

    @Nested
    @DisplayName("buildToolSpecification - 构建工具规格")
    class BuildToolSpecification {

        /**
         * 工具未配置 skillId 时应回退为 tool_<id> 且参数为空对象 Schema
         */
        @Test
        void shouldFallbackWhenConfigBlank() {
            ToolEntity entity = entity("t1", "  ");
            entity.setDescription("未关联技能");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getToolId()).isEqualTo("t1");
            assertThat(spec.getName()).isEqualTo("tool_t1");
            assertThat(spec.getDescription()).isEqualTo("未关联技能");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.SKILL);
            assertThat(spec.getParameters()).containsEntry("type", "object");
        }

        /**
         * 配置了 skillId 时应按技能构建：名称为 skill_<id>，描述优先取技能描述
         */
        @Test
        void shouldBuildFromSkillWhenConfigPresent() {
            SkillEntity skill = skill("s1", "翻译技能", "active");
            skill.setDescription("把文本翻译为英文");
            skill.setParameters("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}");
            when(skillService.getById("s1")).thenReturn(skill);

            ToolSpecification spec = handler.buildToolSpecification(entity("t1", "s1"));

            assertThat(spec.getToolId()).isEqualTo("s1");
            assertThat(spec.getName()).isEqualTo("skill_s1");
            assertThat(spec.getDescription()).isEqualTo("把文本翻译为英文");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.SKILL);
            assertThat(spec.getParameters()).containsKey("properties");
        }

        /**
         * 技能不存在时返回 null（由上游跳过该工具）
         */
        @Test
        void shouldReturnNullWhenSkillMissing() {
            when(skillService.getById("missing")).thenReturn(null);

            ToolSpecification spec = handler.buildToolSpecification(entity("t1", "missing"));

            assertThat(spec).isNull();
        }

        /**
         * 技能描述的 null 回退到技能名称
         */
        @Test
        void shouldFallbackSkillDescriptionToName() {
            when(skillService.getById("s2")).thenReturn(skill("s2", "摘要技能", "active"));

            ToolSpecification spec = handler.buildToolSpecification(entity("t1", "s2"));

            assertThat(spec.getDescription()).isEqualTo("摘要技能");
            assertThat(spec.getParameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute - 执行技能")
    class Execute {

        /**
         * 工具未关联技能 ID 时直接返回失败
         */
        @Test
        void shouldFailWhenSkillIdMissing() {
            ToolExecuteResultDTO result = handler.execute(entity("t1", null), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("工具未关联技能 ID");
        }

        /**
         * 技能不存在时返回“技能不存在”错误
         */
        @Test
        void shouldFailWhenSkillNotFound() {
            when(skillService.getById("s404")).thenReturn(null);

            ToolExecuteResultDTO result = handler.execute("s404", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("技能不存在: s404");
        }

        /**
         * 技能状态非 active 时返回“技能未激活”错误
         */
        @Test
        void shouldFailWhenSkillInactive() {
            when(skillService.getById("s1")).thenReturn(skill("s1", "停用技能", "inactive"));

            ToolExecuteResultDTO result = handler.execute("s1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("技能未激活: 停用技能");
        }

        /**
         * 知识型技能（无 content）返回描述作为输出
         */
        @Test
        void shouldReturnDescriptionForKnowledgeSkill() {
            SkillEntity s = skill("s1", "知识技能", "active");
            s.setDescription("这是一段领域知识");
            when(skillService.getById("s1")).thenReturn(s);

            ToolExecuteResultDTO result = handler.execute("s1", Map.of());

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("这是一段领域知识");
        }

        /**
         * 知识型技能描述为空时输出回退为技能名称
         */
        @Test
        void shouldFallbackOutputToNameForKnowledgeSkill() {
            when(skillService.getById("s1")).thenReturn(skill("s1", "兜底名称", "active"));

            ToolExecuteResultDTO result = handler.execute("s1", Map.of());

            assertThat(result.getOutput()).isEqualTo("兜底名称");
        }

        /**
         * 含 Groovy 内容的技能应真实执行脚本并注入参数
         */
        @Test
        void shouldExecuteGroovySkill() {
            SkillEntity s = skill("s1", "计算技能", "active");
            s.setContent("p * 2");
            when(skillService.getById("s1")).thenReturn(s);

            ToolExecuteResultDTO result = handler.execute("s1", Map.of("p", 21));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo(42);
        }

        /**
         * Groovy 脚本抛异常时返回失败与错误信息
         */
        @Test
        void shouldReturnFailureWhenGroovySkillThrows() {
            SkillEntity s = skill("s1", "异常技能", "active");
            s.setContent("throw new IllegalStateException('技能脚本失败')");
            when(skillService.getById("s1")).thenReturn(s);

            ToolExecuteResultDTO result = handler.execute("s1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("技能脚本失败");
        }

        /**
         * execute(ToolEntity, Map) 正常路径：由工具 config 解析出技能并执行
         */
        @Test
        void shouldExecuteViaToolEntity() {
            SkillEntity s = skill("s9", "问候技能", "active");
            s.setContent("'hi ' + who");
            when(skillService.getById("s9")).thenReturn(s);

            ToolExecuteResultDTO result = handler.execute(entity("t9", "s9"), Map.of("who", "tom"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("hi tom");
        }
    }
}
