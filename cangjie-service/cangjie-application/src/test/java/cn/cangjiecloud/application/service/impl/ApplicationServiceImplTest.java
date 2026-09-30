package cn.cangjiecloud.application.service.impl;

import cn.cangjiecloud.application.api.dto.ApplicationCreateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.mapper.ApplicationMapper;
import cn.cangjiecloud.common.exception.ApiException;
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
 * {@link ApplicationServiceImpl} 单元测试。
 * <p>
 * 全程不连数据库：ServiceImpl 的 save/updateById/removeById 等默认方法为 final，
 * 无法被 spy 拦截，因此注入 mock 的 {@link ApplicationMapper} 作为 baseMapper 承载持久化打桩；
 * getById/getOne/page 等非 final 方法仍由 spy 拦截。预注册 TableInfo 让 Wrapper 离线可解析列名。
 * </p>
 */
class ApplicationServiceImplTest {

    private ApplicationMapper mapper;
    private ApplicationServiceImpl service;

    @BeforeAll
    // 预注册实体表结构：LambdaQueryWrapper 的 SFunction -> 列名解析在离线环境可用
    static void installTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApplicationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApplicationEntity.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(ApplicationMapper.class);
        service = spy(new ApplicationServiceImpl());
        // 注入 baseMapper：承载 final 默认方法（save/updateById/removeById）的底层调用
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private static ApplicationEntity existing(String id, String status) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setName("原应用");
        entity.setStatus(status);
        return entity;
    }

    private static ApplicationCreateDTO dto() {
        ApplicationCreateDTO dto = new ApplicationCreateDTO();
        dto.setName("新应用");
        dto.setDescription("一个应用");
        dto.setType("agent");
        dto.setModelId("model-1");
        dto.setKnowledgeBaseIds(List.of("kb-1", "kb-2"));
        dto.setPromptTemplateId("pt-1");
        dto.setSkillIds(List.of("s1"));
        dto.setRuleIds(List.of());
        dto.setToolIds(null);
        dto.setSuggestions(List.of("问题1"));
        dto.setIcon("icon");
        dto.setTokenQuota(500L);
        return dto;
    }

    private ApplicationEntity capturedSaved() {
        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Create {

        @Test
        void shouldConvertDtoAndPersistDraftEntity() {
            ApplicationEntity result = service.create(dto());

            ApplicationEntity saved = capturedSaved();
            assertThat(saved).isSameAs(result);
            assertThat(saved.getName()).isEqualTo("新应用");
            assertThat(saved.getType()).isEqualTo("agent");
            assertThat(saved.getModelId()).isEqualTo("model-1");
            assertThat(saved.getKnowledgeBaseIds()).isEqualTo("[\"kb-1\",\"kb-2\"]");
            assertThat(saved.getPromptTemplateId()).isEqualTo("pt-1");
            assertThat(saved.getSkillIds()).isEqualTo("[\"s1\"]");
            assertThat(saved.getRuleIds()).isEqualTo("[]");
            assertThat(saved.getToolIds()).isEqualTo("[]");
            assertThat(saved.getSuggestions()).isEqualTo("[\"问题1\"]");
            assertThat(saved.getIcon()).isEqualTo("icon");
            assertThat(saved.getStatus()).isEqualTo("draft");
            assertThat(saved.getTokenQuota()).isEqualTo(500L);
        }

        @Test
        void shouldApplyDefaultsForNullableOptions() {
            ApplicationCreateDTO dto = new ApplicationCreateDTO();
            dto.setName("最小应用");
            dto.setType("chat");

            service.create(dto);

            ApplicationEntity saved = capturedSaved();
            assertThat(saved.getMemoryEnabled()).isFalse();
            assertThat(saved.getMaxTurns()).isEqualTo(20);
            assertThat(saved.getTemperature()).isEqualTo(0.7);
            assertThat(saved.getTokenQuota()).isZero();
            assertThat(saved.getKnowledgeBaseIds()).isEqualTo("[]");
        }

        @Test
        void shouldRespectExplicitlyProvidedOptions() {
            ApplicationCreateDTO dto = dto();
            dto.setMemoryEnabled(true);
            dto.setMaxTurns(99);
            dto.setTemperature(0.1d);

            service.create(dto);

            ApplicationEntity saved = capturedSaved();
            assertThat(saved.getMemoryEnabled()).isTrue();
            assertThat(saved.getMaxTurns()).isEqualTo(99);
            assertThat(saved.getTemperature()).isEqualTo(0.1);
        }
    }

    @Nested
    class Update {

        @Test
        void shouldThrowWhenApplicationMissing() {
            doReturn(null).when(service).getById("nope");

            assertThatThrownBy(() -> service.update("nope", dto()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
            verify(mapper, never()).updateById(any(ApplicationEntity.class));
        }

        @Test
        void shouldOnlyCopyNonBlankFields() {
            ApplicationEntity entity = existing("a1", "draft");
            doReturn(entity).when(service).getById("a1");

            ApplicationCreateDTO patch = new ApplicationCreateDTO();
            patch.setName("改名");
            patch.setType(null);
            patch.setDescription("   ");
            patch.setSkillIds(List.of("new-skill"));

            ApplicationEntity result = service.update("a1", patch);

            assertThat(result).isSameAs(entity);
            assertThat(result.getName()).isEqualTo("改名");
            assertThat(result.getType()).isNull();
            assertThat(result.getSkillIds()).isEqualTo("[\"new-skill\"]");
            verify(mapper).updateById(entity);
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldRemoveWhenEntityExists() {
            doReturn(existing("a1", "draft")).when(service).getById("a1");

            service.delete("a1");

            verify(mapper).deleteById("a1");
        }

        @Test
        void shouldDoNothingWhenEntityMissing() {
            doReturn(null).when(service).getById("a1");

            service.delete("a1");

            verify(mapper, never()).deleteById(any());
        }
    }

    @Nested
    class PageQuery {

        @Test
        @SuppressWarnings("unchecked")
        void shouldQueryWithoutFiltersAndReturnPage() {
            Page<ApplicationEntity> resultPage = new Page<>(1, 10);
            resultPage.setRecords(List.of(existing("a1", "published")));
            doReturn(resultPage).when(service).page(any(Page.class), any(Wrapper.class));

            IPage<ApplicationEntity> result = service.pageQuery(null, null, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            ArgumentCaptor<Wrapper<ApplicationEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            assertThat(captor.getValue().getTargetSql()).doesNotContain("LIKE");
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldBuildKeywordAndTypeConditions() {
            Page<ApplicationEntity> resultPage = new Page<>(1, 10);
            doReturn(resultPage).when(service).page(any(Page.class), any(Wrapper.class));

            service.pageQuery("客服", "agent", 2, 5);

            ArgumentCaptor<Wrapper<ApplicationEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(Page.class), captor.capture());
            String sql = captor.getValue().getTargetSql();
            assertThat(sql).contains("LIKE").contains("type =").contains("ORDER BY");
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldFallbackNullPagingParamsToDefaults() {
            doReturn(new Page<ApplicationEntity>(1, 10))
                    .when(service).page(any(Page.class), any(Wrapper.class));

            service.pageQuery(null, null, null, null);

            ArgumentCaptor<Page<ApplicationEntity>> captor = ArgumentCaptor.forClass(Page.class);
            verify(service).page(captor.capture(), any(Wrapper.class));
            assertThat(captor.getValue().getCurrent()).isEqualTo(1);
            assertThat(captor.getValue().getSize()).isEqualTo(10);
        }
    }

    @Nested
    class Publish {

        @Test
        void shouldThrowWhenApplicationMissing() {
            doReturn(null).when(service).getById("a1");

            assertThatThrownBy(() -> service.publish("a1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("应用不存在");
        }

        @Test
        void shouldGenerateApikeyAndMarkPublishedWhenKeyMissing() {
            ApplicationEntity entity = existing("a1", "draft");
            doReturn(entity).when(service).getById("a1");

            ApplicationEntity result = service.publish("a1");

            assertThat(result.getStatus()).isEqualTo("published");
            assertThat(result.getApikey()).startsWith("cj_").hasSize(43);
            verify(mapper).updateById(entity);
        }

        @Test
        void shouldKeepExistingApikeyWhenAlreadySet() {
            ApplicationEntity entity = existing("a1", "published");
            entity.setApikey("cj_existing");
            doReturn(entity).when(service).getById("a1");

            service.publish("a1");

            assertThat(entity.getApikey()).isEqualTo("cj_existing");
        }
    }

    @Nested
    class GetByApikey {

        @Test
        void shouldReturnNullWhenKeyBlank() {
            assertThat(service.getByApikey(null)).isNull();
            assertThat(service.getByApikey("  ")).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldQueryPublishedApplicationByKey() {
            ApplicationEntity app = existing("a1", "published");
            app.setApikey("cj_key");
            doReturn(app).when(service).getOne(any(Wrapper.class));

            ApplicationEntity result = service.getByApikey("cj_key");

            assertThat(result).isSameAs(app);
            ArgumentCaptor<Wrapper<ApplicationEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture());
            String sql = captor.getValue().getTargetSql();
            assertThat(sql).contains("apikey =").contains("status =").contains("LIMIT 1");
        }
    }
}
