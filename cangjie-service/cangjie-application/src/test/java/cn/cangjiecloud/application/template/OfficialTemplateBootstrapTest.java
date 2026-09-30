package cn.cangjiecloud.application.template;

import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.mapper.ApplicationTemplateMapper;
import cn.cangjiecloud.application.service.IApplicationTemplateService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OfficialTemplateBootstrap} 官方模板引导单元测试。
 * <p>
 * 不加载 Spring 上下文：mock 模板服务，通过真实 classpath:official-templates 资源验证
 * 启动时幂等导入、版本升级、用户自定义保护与异常容忍等行为。
 * </p>
 */
class OfficialTemplateBootstrapTest {

    /** main/resources/official-templates 下内置模板文件数量 */
    private static final int BUNDLE_COUNT = 8;

    private IApplicationTemplateService templateService;
    private ApplicationArguments arguments;
    private OfficialTemplateBootstrap bootstrap;

    @BeforeAll
    static void installTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApplicationTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApplicationTemplateEntity.class);
    }

    @BeforeEach
    void setUp() {
        templateService = mock(IApplicationTemplateService.class);
        arguments = mock(ApplicationArguments.class);
        bootstrap = new OfficialTemplateBootstrap(templateService);
        ReflectionTestUtils.setField(bootstrap, "importBuiltin", true);
    }

    private static ApplicationTemplateEntity existing(boolean builtin, Integer version) {
        ApplicationTemplateEntity entity = new ApplicationTemplateEntity();
        entity.setBuiltin(builtin);
        entity.setVersion(version);
        return entity;
    }

    @Nested
    class Toggle {

        @Test
        void shouldSkipEverythingWhenImportDisabled() {
            ReflectionTestUtils.setField(bootstrap, "importBuiltin", false);

            bootstrap.run(arguments);

            verify(templateService, never()).getOne(any());
            verify(templateService, never()).importBundle(any(), anyBoolean());
        }
    }

    @Nested
    class ClasspathImport {

        @Test
        void shouldImportEveryBundledTemplateWhenNoneExists() {
            when(templateService.getOne(any())).thenReturn(null);

            bootstrap.run(arguments);

            // 全部以内置身份导入
            verify(templateService, times(BUNDLE_COUNT)).importBundle(any(), eq(true));
        }

        @Test
        void shouldBeIdempotentAcrossRuns() {
            // 第二次启动时模板均已存在但版本相同，不应重复导入
            when(templateService.getOne(any())).thenReturn(existing(true, 1));

            bootstrap.run(arguments);

            verify(templateService, never()).importBundle(any(), anyBoolean());
        }

        @Test
        void shouldUpgradeWhenBuiltinVersionIsOlder() {
            // 库中内置版本为 0（或 null），内置资源版本更高 → 升级覆盖
            when(templateService.getOne(any())).thenReturn(existing(true, 0));

            bootstrap.run(arguments);

            verify(templateService, times(BUNDLE_COUNT)).importBundle(any(), eq(true));
        }

        @Test
        void shouldNotOverwriteUserCustomizedBuiltinTemplate() {
            // 用户改过（builtin=false），即使内置版本更高也不覆盖
            when(templateService.getOne(any())).thenReturn(existing(false, 0));

            bootstrap.run(arguments);

            verify(templateService, never()).importBundle(any(), anyBoolean());
        }

        @Test
        void shouldSkipWhenExistingBuiltinVersionIsNewer() {
            when(templateService.getOne(any())).thenReturn(existing(true, 999));

            bootstrap.run(arguments);

            verify(templateService, never()).importBundle(any(), anyBoolean());
        }

        @Test
        void shouldTolerateImportFailureAndContinueStartup() {
            when(templateService.getOne(any())).thenReturn(null);
            when(templateService.importBundle(any(), anyBoolean()))
                    .thenThrow(new RuntimeException("boom"));

            // 单个模板失败只记录告警，不影响启动，也不向外抛异常
            assertThatCode(() -> bootstrap.run(arguments)).doesNotThrowAnyException();
            verify(templateService, times(BUNDLE_COUNT)).importBundle(any(), eq(true));
        }
    }
}
