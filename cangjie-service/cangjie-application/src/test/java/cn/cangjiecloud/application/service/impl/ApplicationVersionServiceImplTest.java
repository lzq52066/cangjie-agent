package cn.cangjiecloud.application.service.impl;

import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationVersionEntity;
import cn.cangjiecloud.application.mapper.ApplicationMapper;
import cn.cangjiecloud.application.mapper.ApplicationVersionMapper;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationVersionServiceImpl} 应用版本服务单元测试。
 * <p>
 * 不连数据库：构造器注入 mock 应用服务与应用 Mapper，spy 拦截版本表的
 * getById/getOne/save/removeById/page；预注册 TableInfo 让 Wrapper 离线可解析列名。
 * </p>
 */
class ApplicationVersionServiceImplTest {

    private IApplicationService applicationService;
    private ApplicationMapper applicationMapper;
    private ApplicationVersionMapper versionMapper;
    private ApplicationVersionServiceImpl service;

    @BeforeAll
    static void installTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApplicationVersionMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApplicationVersionEntity.class);
    }

    @BeforeEach
    void setUp() {
        applicationService = mock(IApplicationService.class);
        applicationMapper = mock(ApplicationMapper.class);
        versionMapper = mock(ApplicationVersionMapper.class);
        service = spy(new ApplicationVersionServiceImpl(applicationService, applicationMapper));
        // 注入版本表 baseMapper：承载 save/removeById 等 final 默认方法的底层调用
        ReflectionTestUtils.setField(service, "baseMapper", versionMapper);
    }

    private static ApplicationEntity application(String id) {
        ApplicationEntity app = new ApplicationEntity();
        app.setId(id);
        app.setName("应用");
        app.setDescription("desc");
        app.setType("agent");
        app.setModelId("m1");
        app.setKnowledgeBaseIds("[]");
        app.setPromptTemplateId("pt-1");
        app.setSkillIds("[]");
        app.setRuleIds("[]");
        app.setMemoryEnabled(true);
        app.setMaxTurns(20);
        app.setTemperature(0.7d);
        app.setConfig("{}");
        app.setSuggestions("[]");
        app.setIcon("icon");
        return app;
    }

    private static ApplicationVersionEntity version(String id, int version) {
        ApplicationVersionEntity v = new ApplicationVersionEntity();
        v.setId(id);
        v.setApplicationId("app-1");
        v.setVersion(version);
        return v;
    }

    @Nested
    class GetVersion {

        @Test
        void shouldReturnNullWhenMissing() {
            doReturn(null).when(service).getById("v1");

            assertThat(service.getVersion("v1")).isNull();
        }

        @Test
        void shouldConvertEntityToDto() {
            ApplicationVersionEntity entity = version("v1", 2);
            entity.setName("应用");
            entity.setPublishBy("alice");
            doReturn(entity).when(service).getById("v1");

            ApplicationVersionDTO dto = service.getVersion("v1");

            assertThat(dto.getId()).isEqualTo("v1");
            assertThat(dto.getVersion()).isEqualTo(2);
            assertThat(dto.getApplicationId()).isEqualTo("app-1");
            assertThat(dto.getName()).isEqualTo("应用");
            assertThat(dto.getPublishBy()).isEqualTo("alice");
        }
    }

    @Nested
    class PageByApplication {

        @Test
        @SuppressWarnings("unchecked")
        void shouldPageVersionsOrderedByVersionDescAndConvert() {
            Page<ApplicationVersionEntity> resultPage = new Page<>(1, 10);
            resultPage.setRecords(List.of(version("v2", 2), version("v1", 1)));
            doReturn(resultPage).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ApplicationVersionDTO> result = service.pageByApplication("app-1", 1, 10);

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getRecords().get(0).getId()).isEqualTo("v2");
            ArgumentCaptor<Wrapper<ApplicationVersionEntity>> captor =
                    ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            assertThat(captor.getValue().getTargetSql())
                    .contains("application_id =").containsIgnoringCase("ORDER BY version DESC");
        }
    }

    @Nested
    class Publish {

        @Test
        void shouldThrowWhenApplicationMissing() {
            when(applicationService.getById("app-1")).thenReturn(null);

            assertThatThrownBy(() -> service.publish("app-1", "alice"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
        }

        @Test
        void shouldSnapshotNextVersionAndSave() {
            when(applicationService.getById("app-1")).thenReturn(application("app-1"));
            // 当前最新版本为 v3，新版本应为 v4
            doReturn(version("v3", 3)).when(service).getOne(any(Wrapper.class));

            ApplicationVersionDTO dto = service.publish("app-1", "alice");

            ArgumentCaptor<ApplicationVersionEntity> captor =
                    ArgumentCaptor.forClass(ApplicationVersionEntity.class);
            verify(versionMapper).insert(captor.capture());
            ApplicationVersionEntity saved = captor.getValue();
            assertThat(saved.getApplicationId()).isEqualTo("app-1");
            assertThat(saved.getVersion()).isEqualTo(4);
            assertThat(saved.getName()).isEqualTo("应用");
            assertThat(saved.getType()).isEqualTo("agent");
            assertThat(saved.getPromptTemplateId()).isEqualTo("pt-1");
            assertThat(saved.getSnapshot()).isNotBlank();
            assertThat(saved.getPublishLog()).isEqualTo("发布版本 v4");
            assertThat(saved.getPublishBy()).isEqualTo("alice");

            assertThat(dto.getVersion()).isEqualTo(4);
        }

        @Test
        void shouldStartFromVersionOneAndFallbackOperatorToSystem() {
            when(applicationService.getById("app-1")).thenReturn(application("app-1"));
            doReturn(null).when(service).getOne(any(Wrapper.class));

            service.publish("app-1", null);

            ArgumentCaptor<ApplicationVersionEntity> captor =
                    ArgumentCaptor.forClass(ApplicationVersionEntity.class);
            verify(versionMapper).insert(captor.capture());
            assertThat(captor.getValue().getVersion()).isEqualTo(1);
            assertThat(captor.getValue().getPublishBy()).isEqualTo("system");
        }
    }

    @Nested
    class Rollback {

        @Test
        void shouldThrowWhenTargetVersionMissing() {
            doReturn(null).when(service).getById("v1");

            assertThatThrownBy(() -> service.rollback("app-1", "v1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("目标版本不存在");
        }

        @Test
        void shouldThrowWhenVersionBelongsToAnotherApplication() {
            ApplicationVersionEntity target = version("v1", 1);
            target.setApplicationId("app-other");
            doReturn(target).when(service).getById("v1");

            assertThatThrownBy(() -> service.rollback("app-1", "v1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("版本与应用不匹配");
        }

        @Test
        void shouldThrowWhenCurrentApplicationMissing() {
            doReturn(version("v1", 1)).when(service).getById("v1");
            when(applicationService.getById("app-1")).thenReturn(null);

            assertThatThrownBy(() -> service.rollback("app-1", "v1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
        }

        @Test
        void shouldRestoreFieldsFromSnapshotViaApplicationMapper() {
            ApplicationEntity snapshotApp = application("app-1");
            snapshotApp.setName("快照中的名字");
            ApplicationVersionEntity target = version("v1", 1);
            target.setSnapshot(JsonUtils.toJSONString(snapshotApp));
            doReturn(target).when(service).getById("v1");

            ApplicationEntity current = application("app-1");
            current.setName("当前名字");
            when(applicationService.getById("app-1")).thenReturn(current);

            service.rollback("app-1", "v1");

            ArgumentCaptor<ApplicationEntity> captor =
                    ArgumentCaptor.forClass(ApplicationEntity.class);
            verify(applicationMapper).updateById(captor.capture());
            ApplicationEntity restored = captor.getValue();
            assertThat(restored).isSameAs(current);
            assertThat(restored.getName()).isEqualTo("快照中的名字");
            assertThat(restored.getType()).isEqualTo("agent");
            assertThat(restored.getIcon()).isEqualTo("icon");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldThrowWhenVersionMissing() {
            doReturn(null).when(service).getById("v1");

            assertThatThrownBy(() -> service.delete("v1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("版本不存在");
        }

        @Test
        void shouldRejectDeletingLatestVersion() {
            ApplicationVersionEntity target = version("v3", 3);
            doReturn(target).when(service).getById("v3");
            doReturn(version("v3", 3)).when(service).getOne(any(Wrapper.class));

            assertThatThrownBy(() -> service.delete("v3"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("无法删除最新版本");
            verify(versionMapper, never()).deleteById(any());
        }

        @Test
        void shouldRemoveOlderVersion() {
            ApplicationVersionEntity target = version("v1", 1);
            doReturn(target).when(service).getById("v1");
            doReturn(version("v3", 3)).when(service).getOne(any(Wrapper.class));

            service.delete("v1");

            verify(versionMapper).deleteById("v1");
        }
    }
}
