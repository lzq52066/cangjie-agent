package cn.cangjiecloud.application.service.impl;

import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.mapper.ApplicationTemplateMapper;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.service.IPromptTemplateService;
import cn.cangjiecloud.workflow.entity.WorkflowEntity;
import cn.cangjiecloud.workflow.service.IWorkflowService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationTemplateServiceImpl} Bundle 模板服务单元测试。
 * <p>
 * 不连数据库：spy 拦截 save/getById/updateById/getOne/page，应用、提示词、工作流服务
 * 全部 Mockito mock；预注册 TableInfo 保证 Wrapper 离线可解析列名。
 * </p>
 */
class ApplicationTemplateServiceImplTest {

    private IApplicationService applicationService;
    private IPromptTemplateService promptTemplateService;
    private IWorkflowService workflowService;
    private ApplicationTemplateMapper mapper;
    private ApplicationTemplateServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApplicationTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApplicationTemplateEntity.class);
    }

    @BeforeEach
    void setUp() {
        applicationService = mock(IApplicationService.class);
        promptTemplateService = mock(IPromptTemplateService.class);
        workflowService = mock(IWorkflowService.class);
        mapper = mock(ApplicationTemplateMapper.class);
        service = spy(new ApplicationTemplateServiceImpl(
                applicationService, promptTemplateService, workflowService));
        // 注入 baseMapper：save/updateById/removeById 等 final 默认方法最终调用 mapper
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private static ApplicationEntity application(String id) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setName("客服助手");
        app.setDescription("desc");
        app.setType("agent");
        app.setPromptTemplateId("pt-1");
        app.setIcon("icon");
        return app;
    }

    private static ApplicationTemplateEntity template(String id, String status) {
        ApplicationTemplateEntity t = new ApplicationTemplateEntity();
        t.setId(id);
        t.setName("模板");
        t.setStatus(status);
        t.setUseCount(1);
        return t;
    }

    /** 让 applicationService.save 回填 ID，模拟数据库赋值主键 */
    private void stubSaveAssignsId(String id) {
        doAnswer(invocation -> {
            ((ApplicationEntity) invocation.getArgument(0)).setId(id);
            return true;
        }).when(applicationService).save(any(ApplicationEntity.class));
    }

    @Nested
    class SaveFromApplication {

        @Test
        void shouldThrowWhenApplicationMissing() {
            when(applicationService.getById("a1")).thenReturn(null);

            assertThatThrownBy(() -> service.saveFromApplication("a1", null, null, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
        }

        @Test
        void shouldAssembleFullBundleWithPromptAndWorkflow() {
            ApplicationEntity app = application("a1");
            when(applicationService.getById("a1")).thenReturn(app);
            PromptTemplateEntity pt = new PromptTemplateEntity();
            pt.setName("提示词");
            pt.setContent("内容");
            pt.setCategory("cat");
            pt.setDescription("pt-desc");
            pt.setVariables("[]");
            when(promptTemplateService.getById("pt-1")).thenReturn(pt);
            WorkflowEntity wf = new WorkflowEntity();
            wf.setName("工作流");
            wf.setNodes("[{\"id\":\"n1\"}]");
            wf.setEdges("[{\"s\":1}]");
            wf.setVariables("[]");
            when(workflowService.getByApplicationId("a1")).thenReturn(wf);

            ApplicationTemplateEntity result =
                    service.saveFromApplication("a1", "自定义名", "自定义描述", "客服");

            assertThat(result.getName()).isEqualTo("自定义名");
            assertThat(result.getDescription()).isEqualTo("自定义描述");
            assertThat(result.getCategory()).isEqualTo("客服");
            assertThat(result.getAppType()).isEqualTo("agent");
            assertThat(result.getIcon()).isEqualTo("icon");
            assertThat(result.getStatus()).isEqualTo("published");
            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getBuiltin()).isFalse();
            assertThat(result.getUseCount()).isZero();

            ObjectNode bundle = JsonUtils.parseObject(result.getSnapshot());
            assertThat(bundle.has("application")).isTrue();
            assertThat(bundle.has("promptTemplate")).isTrue();
            assertThat(bundle.has("workflow")).isTrue();
            // 实例相关字段必须从快照剔除
            assertThat(bundle.get("application").has("apikey")).isFalse();
            assertThat(bundle.get("application").has("id")).isFalse();
            verify(mapper).insert(result);
        }

        @Test
        void shouldFallbackTemplateNameAndSkipMissingPrompt() {
            ApplicationEntity app = application("a1");
            app.setPromptTemplateId(null);
            when(applicationService.getById("a1")).thenReturn(app);
            when(workflowService.getByApplicationId("a1")).thenReturn(null);

            ApplicationTemplateEntity result =
                    service.saveFromApplication("a1", null, null, null);

            assertThat(result.getName()).isEqualTo("客服助手 模板");
            assertThat(result.getDescription()).isEqualTo("desc");
            ObjectNode bundle = JsonUtils.parseObject(result.getSnapshot());
            assertThat(bundle.has("promptTemplate")).isFalse();
            assertThat(bundle.has("workflow")).isFalse();
        }
    }

    @Nested
    class PageTemplates {

        @Test
        @SuppressWarnings("unchecked")
        void shouldReturnPageOfPublishedTemplates() {
            Page<ApplicationTemplateEntity> resultPage = new Page<>(1, 10);
            resultPage.setRecords(List.of(template("t1", "published")));
            doReturn(resultPage).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ApplicationTemplateEntity> result = service.pageTemplates("客服", 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            ArgumentCaptor<Wrapper<ApplicationTemplateEntity>> captor =
                    ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            String sql = captor.getValue().getTargetSql();
            assertThat(sql).contains("status =").contains("ORDER BY");
        }
    }

    @Nested
    class CreateFromTemplate {

        private static final String SNAPSHOT = """
                {
                  "application": {
                    "type": "agent",
                    "description": "snapshot desc",
                    "modelId": "m1",
                    "knowledgeBaseIds": "[\\"kb-1\\"]",
                    "skillIds": "[]",
                    "ruleIds": "[]",
                    "toolIds": "[]",
                    "memoryEnabled": true,
                    "maxTurns": 25,
                    "temperature": 0.5,
                    "suggestions": "[]",
                    "icon": "icon",
                    "ragMode": "simple"
                  },
                  "promptTemplate": {
                    "name": "提示词",
                    "content": "你是助手",
                    "category": "通用",
                    "description": "pt desc",
                    "variables": "[]"
                  },
                  "workflow": {
                    "name": "工作流",
                    "description": "wf desc",
                    "nodes": "[{\\"id\\":\\"start\\"}]",
                    "edges": "[]",
                    "variables": "[]"
                  }
                }
                """;

        @Test
        void shouldThrowWhenTemplateMissing() {
            doReturn(null).when(service).getById("t1");

            assertThatThrownBy(() -> service.createFromTemplate("t1", null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("模板不存在");
        }

        @Test
        void shouldThrowWhenTemplateOffline() {
            doReturn(template("t1", "offline")).when(service).getById("t1");

            assertThatThrownBy(() -> service.createFromTemplate("t1", null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("模板已下线，无法使用");
        }

        @Test
        void shouldThrowWhenSnapshotCorrupted() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setSnapshot("not-json");
            doReturn(t).when(service).getById("t1");

            assertThatThrownBy(() -> service.createFromTemplate("t1", null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("模板快照损坏");
        }

        @Test
        void shouldCascadeCreatePromptAppAndWorkflow() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setName("模板名");
            t.setSnapshot(SNAPSHOT);
            doReturn(t).when(service).getById("t1");
            PromptTemplateEntity createdPrompt = new PromptTemplateEntity();
            createdPrompt.setId("pt-new");
            when(promptTemplateService.create(any(PromptTemplateEntity.class)))
                    .thenReturn(createdPrompt);
            stubSaveAssignsId("app-new");
            WorkflowEntity createdWorkflow = new WorkflowEntity();
            createdWorkflow.setId("wf-new");
            when(workflowService.create(any(WorkflowEntity.class))).thenReturn(createdWorkflow);

            ApplicationEntity result = service.createFromTemplate("t1", "我的新应用");

            assertThat(result.getId()).isEqualTo("app-new");
            assertThat(result.getName()).isEqualTo("我的新应用");
            assertThat(result.getDescription()).isEqualTo("snapshot desc");
            assertThat(result.getType()).isEqualTo("agent");
            assertThat(result.getPromptTemplateId()).isEqualTo("pt-new");
            assertThat(result.getStatus()).isEqualTo("draft");
            assertThat(result.getTokenQuota()).isZero();

            ArgumentCaptor<WorkflowEntity> wfCaptor = ArgumentCaptor.forClass(WorkflowEntity.class);
            verify(workflowService).create(wfCaptor.capture());
            assertThat(wfCaptor.getValue().getApplicationId()).isEqualTo("app-new");
            assertThat(wfCaptor.getValue().getStatus()).isEqualTo("published");
            // 使用计数 +1
            assertThat(t.getUseCount()).isEqualTo(2);
            verify(mapper).updateById(t);
        }

        @Test
        void shouldFallbackToTemplateNameAndFallbackAppTypeWhenAppSectionMissing() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setName("模板名");
            t.setAppType("chat");
            // 旧格式：根对象即应用快照
            t.setSnapshot("{\"description\":\"old\"}");
            doReturn(t).when(service).getById("t1");
            stubSaveAssignsId("app-old");

            ApplicationEntity result = service.createFromTemplate("t1", null);

            assertThat(result.getName()).isEqualTo("模板名");
            assertThat(result.getDescription()).isEqualTo("old");
            assertThat(result.getType()).isEqualTo("chat");
            verify(promptTemplateService, org.mockito.Mockito.never())
                    .create(any(PromptTemplateEntity.class));
        }
    }

    @Nested
    class ImportBundle {

        @Test
        void shouldThrowWhenJsonInvalid() {
            assertThatThrownBy(() -> service.importBundle("oops", false))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("模板 JSON 解析失败");
        }

        @Test
        void shouldThrowWhenNameMissing() {
            assertThatThrownBy(() -> service.importBundle("{\"templateKey\":\"k\"}", false))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("模板缺少 name 字段");
        }

        @Test
        void shouldSaveNewTemplateWithDefaults() {
            String json = "{\"templateKey\":\"k1\",\"name\":\"新模板\"}";
            doReturn(null).when(service).getOne(any(Wrapper.class));

            ApplicationTemplateEntity result = service.importBundle(json, false);

            assertThat(result.getTemplateKey()).isEqualTo("k1");
            assertThat(result.getName()).isEqualTo("新模板");
            assertThat(result.getAppType()).isEqualTo("chat");
            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo("published");
            assertThat(result.getBuiltin()).isFalse();
            assertThat(result.getUseCount()).isZero();
            verify(mapper).insert(result);
        }

        @Test
        void shouldUpdateExistingTemplateWhenTemplateKeyMatches() {
            String json = "{\"templateKey\":\"k1\",\"name\":\"升级版\",\"version\":3}";
            ApplicationTemplateEntity existing = template("t1", "published");
            existing.setTemplateKey("k1");
            doReturn(existing).when(service).getOne(any(Wrapper.class));

            ApplicationTemplateEntity result = service.importBundle(json, true);

            assertThat(result).isSameAs(existing);
            assertThat(result.getName()).isEqualTo("升级版");
            assertThat(result.getVersion()).isEqualTo(3);
            assertThat(result.getBuiltin()).isTrue();
            verify(mapper).updateById(existing);
            verify(mapper, org.mockito.Mockito.never()).insert(any(ApplicationTemplateEntity.class));
        }

        @Test
        void shouldHonorBuiltinFlagFromBundleContent() {
            String json = "{\"name\":\"x\",\"builtin\":true}";

            ApplicationTemplateEntity result = service.importBundle(json, false);

            assertThat(result.getBuiltin()).isTrue();
        }
    }

    @Nested
    class ExportBundle {

        @Test
        void shouldThrowWhenTemplateMissing() {
            doReturn(null).when(service).getById("t1");

            assertThatThrownBy(() -> service.exportBundle("t1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("模板不存在");
        }

        @Test
        void shouldMergeMetaIntoSelfContainedBundle() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setTemplateKey("key");
            t.setDescription("desc");
            t.setIcon("icon");
            t.setCategory("cat");
            t.setAppType("chat");
            t.setVersion(4);
            t.setSnapshot("{\"application\":{\"name\":\"x\"}}");
            doReturn(t).when(service).getById("t1");

            String json = service.exportBundle("t1");

            ObjectNode bundle = JsonUtils.parseObject(json);
            assertThat(bundle.get("templateKey").asText()).isEqualTo("key");
            assertThat(bundle.get("name").asText()).isEqualTo("模板");
            assertThat(bundle.get("version").asInt()).isEqualTo(4);
            assertThat(bundle.has("application")).isTrue();
        }

        @Test
        void shouldTolerateCorruptedSnapshot() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setSnapshot("broken");
            doReturn(t).when(service).getById("t1");

            String json = service.exportBundle("t1");

            assertThat(JsonUtils.parseObject(json).get("name").asText()).isEqualTo("模板");
        }
    }

    @Nested
    class DeleteTemplate {

        @Test
        void shouldDoNothingWhenMissing() {
            doReturn(null).when(service).getById("t1");

            service.deleteTemplate("t1");

            verify(mapper, org.mockito.Mockito.never()).deleteById(any());
        }

        @Test
        void shouldRejectDeletingBuiltinTemplate() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setBuiltin(true);
            doReturn(t).when(service).getById("t1");

            assertThatThrownBy(() -> service.deleteTemplate("t1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("官方内置模板不可删除");
            verify(mapper, org.mockito.Mockito.never()).deleteById(any());
        }

        @Test
        void shouldRemoveCustomTemplate() {
            ApplicationTemplateEntity t = template("t1", "published");
            t.setBuiltin(false);
            doReturn(t).when(service).getById("t1");

            service.deleteTemplate("t1");

            verify(mapper).deleteById("t1");
        }
    }
}
