package cn.cangjiecloud.prompt.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.prompt.entity.CommandEntity;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.service.ICommandService;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import cn.cangjiecloud.prompt.service.IRuleService;
import cn.cangjiecloud.prompt.service.ISkillService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PromptController} 单元测试：仅验证参数透传与统一响应包装（不含业务逻辑）。
 */
@ExtendWith(MockitoExtension.class)
class PromptControllerTest {

    @Mock
    private IPromptTemplateService promptTemplateService;

    @Mock
    private ISkillService skillService;

    @Mock
    private IRuleService ruleService;

    @Mock
    private ICommandService commandService;

    private PromptController controller;

    @BeforeEach
    void setUp() {
        controller = new PromptController(promptTemplateService, skillService, ruleService, commandService);
    }

    private static <T> Page<T> pageOf(List<T> records, long total) {
        Page<T> page = new Page<>(2, 20);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    @Nested
    @DisplayName("提示词模板接口")
    class TemplateApi {

        @Test
        void 分页查询包装为PageResult() {
            PromptTemplateEntity entity = new PromptTemplateEntity();
            entity.setId("t1");
            when(promptTemplateService.pageQuery("摘要", 2, 20)).thenReturn(pageOf(List.of(entity), 7));

            R<PageResult<PromptTemplateEntity>> result = controller.listTemplate("摘要", 2, 20);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).containsExactly(entity);
            assertThat(result.getData().getTotal()).isEqualTo(7);
            assertThat(result.getData().getPage()).isEqualTo(2);
            assertThat(result.getData().getSize()).isEqualTo(20);
        }

        @Test
        void 创建更新查询与删除原样透传() {
            PromptTemplateEntity entity = new PromptTemplateEntity();
            entity.setId("t1");
            entity.setName("摘要模板");
            when(promptTemplateService.create(entity)).thenReturn(entity);
            when(promptTemplateService.update("t1", entity)).thenReturn(entity);
            when(promptTemplateService.getById("t1")).thenReturn(entity);

            assertThat(controller.createTemplate(entity).getData()).isSameAs(entity);
            assertThat(controller.updateTemplate("t1", entity).getData()).isSameAs(entity);
            assertThat(controller.getTemplate("t1").getData()).isSameAs(entity);

            R<Void> deleted = controller.deleteTemplate("t1");
            assertThat(deleted.isSuccess()).isTrue();
            assertThat(deleted.getData()).isNull();
            verify(promptTemplateService).delete("t1");
        }
    }

    @Nested
    @DisplayName("技能接口")
    class SkillApi {

        @Test
        void 分页查询包装为PageResult() {
            SkillEntity entity = new SkillEntity();
            entity.setId("s1");
            when(skillService.pageQuery(null, 1, 10)).thenReturn(pageOf(List.of(entity), 1));

            R<PageResult<SkillEntity>> result = controller.listSkill(null, 1, 10);

            assertThat(result.getData().getList()).containsExactly(entity);
            assertThat(result.getData().getTotal()).isEqualTo(1);
        }

        @Test
        void 创建更新查询与删除原样透传() {
            SkillEntity entity = new SkillEntity();
            entity.setId("s1");
            when(skillService.create(entity)).thenReturn(entity);
            when(skillService.update("s1", entity)).thenReturn(entity);
            when(skillService.getById("s1")).thenReturn(entity);

            assertThat(controller.createSkill(entity).getData()).isSameAs(entity);
            assertThat(controller.updateSkill("s1", entity).getData()).isSameAs(entity);
            assertThat(controller.getSkill("s1").getData()).isSameAs(entity);

            assertThat(controller.deleteSkill("s1").isSuccess()).isTrue();
            verify(skillService).delete("s1");
        }
    }

    @Nested
    @DisplayName("规则接口")
    class RuleApi {

        @Test
        void 分页查询包装为PageResult() {
            RuleEntity entity = new RuleEntity();
            entity.setId("r1");
            when(ruleService.pageQuery("确认", 1, 10)).thenReturn(pageOf(List.of(entity), 1));

            R<PageResult<RuleEntity>> result = controller.listRule("确认", 1, 10);

            assertThat(result.getData().getList()).containsExactly(entity);
            assertThat(result.getData().getPage()).isEqualTo(2);
        }

        @Test
        void 创建更新查询与删除原样透传() {
            RuleEntity entity = new RuleEntity();
            entity.setId("r1");
            when(ruleService.create(entity)).thenReturn(entity);
            when(ruleService.update("r1", entity)).thenReturn(entity);
            when(ruleService.getById("r1")).thenReturn(entity);

            assertThat(controller.createRule(entity).getData()).isSameAs(entity);
            assertThat(controller.updateRule("r1", entity).getData()).isSameAs(entity);
            assertThat(controller.getRule("r1").getData()).isSameAs(entity);

            assertThat(controller.deleteRule("r1").isSuccess()).isTrue();
            verify(ruleService).delete("r1");
        }
    }

    @Nested
    @DisplayName("命令接口")
    class CommandApi {

        @Test
        void 分页查询包装为PageResult() {
            CommandEntity entity = new CommandEntity();
            entity.setId("c1");
            when(commandService.pageQuery(null, 1, 10)).thenReturn(pageOf(List.of(entity), 1));

            R<PageResult<CommandEntity>> result = controller.listCommand(null, 1, 10);

            assertThat(result.getData().getList()).containsExactly(entity);
            assertThat(result.getData().getSize()).isEqualTo(20);
        }

        @Test
        void 创建更新查询与删除原样透传() {
            CommandEntity entity = new CommandEntity();
            entity.setId("c1");
            when(commandService.create(entity)).thenReturn(entity);
            when(commandService.update("c1", entity)).thenReturn(entity);
            when(commandService.getById("c1")).thenReturn(entity);

            assertThat(controller.createCommand(entity).getData()).isSameAs(entity);
            assertThat(controller.updateCommand("c1", entity).getData()).isSameAs(entity);
            assertThat(controller.getCommand("c1").getData()).isSameAs(entity);

            assertThat(controller.deleteCommand("c1").isSuccess()).isTrue();
            verify(commandService).delete("c1");
        }
    }
}
