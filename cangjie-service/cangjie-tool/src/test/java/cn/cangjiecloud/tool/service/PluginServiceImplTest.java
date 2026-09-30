package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.plugin.PluginRegistry;
import cn.cangjiecloud.tool.entity.PluginEntity;
import cn.cangjiecloud.tool.mapper.PluginMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PluginServiceImpl 单元测试
 * <p>
 * 通过注入 mock 的 PluginMapper 拦截全部数据库访问，不启动 Spring 上下文、不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("插件服务实现 - PluginServiceImpl")
class PluginServiceImplTest {

    @Mock
    private PluginRegistry pluginRegistry;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private PluginMapper pluginMapper;

    private PluginServiceImpl service;

    /**
     * 初始化 PluginEntity 的 MyBatis-Plus 元数据，使 LambdaQueryWrapper 可以解析列名
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.tool.mapper.PluginMapper");
        TableInfoHelper.initTableInfo(assistant, PluginEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new PluginServiceImpl(pluginRegistry, applicationContext);
        ReflectionTestUtils.setField(service, "baseMapper", pluginMapper);
    }

    /**
     * 构造插件实体
     */
    private static PluginEntity plugin(String id, String className, Boolean loaded) {
        PluginEntity entity = new PluginEntity();
        entity.setId(id);
        entity.setName("插件-" + id);
        entity.setClassName(className);
        entity.setLoaded(loaded);
        return entity;
    }

    /**
     * 可被反射实例化的测试插件
     */
    public static class SamplePlugin implements Plugin {
        @Override
        public String getName() {
            return "sample";
        }

        @Override
        public String getDescription() {
            return "示例插件";
        }

        @Override
        public String getType() {
            return "tool";
        }

        @Override
        public Object execute(PluginContext context) {
            return "sample-result";
        }
    }

    /**
     * 未实现 Plugin 接口的普通类，用于校验 reload 的类型检查
     */
    public static class NotAPlugin {
    }

    @Nested
    @DisplayName("create - 创建插件")
    class Create {

        /**
         * 未传状态与加载标记时补默认值 active / false 并落库
         */
        @Test
        void shouldCreateWithDefaultStatusAndLoaded() {
            PluginEntity entity = new PluginEntity();
            entity.setName("新插件");

            PluginEntity created = service.create(entity);

            assertThat(created).isSameAs(entity);
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getLoaded()).isFalse();
            verify(pluginMapper).insert(entity);
        }

        /**
         * 显式传入的状态与加载标记不被覆盖
         */
        @Test
        void shouldKeepProvidedStatusAndLoaded() {
            PluginEntity entity = new PluginEntity();
            entity.setStatus("inactive");
            entity.setLoaded(true);

            service.create(entity);

            assertThat(entity.getStatus()).isEqualTo("inactive");
            assertThat(entity.getLoaded()).isTrue();
        }
    }

    @Nested
    @DisplayName("update - 更新插件")
    class Update {

        /**
         * 插件不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingPlugin() {
            when(pluginMapper.selectById("p404")).thenReturn(null);

            assertThatThrownBy(() -> service.update("p404", new PluginEntity()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("插件不存在");
            verify(pluginMapper, never()).updateById(any(PluginEntity.class));
        }

        /**
         * 插件存在时补齐 ID 更新，并返回更新后的最新记录
         */
        @Test
        void shouldUpdateAndReturnReloaded() {
            PluginEntity existing = plugin("p1", "cn.x.Plugin", true);
            PluginEntity reloaded = plugin("p1", "cn.x.Plugin", true);
            reloaded.setName("更新后的插件");
            when(pluginMapper.selectById("p1")).thenReturn(existing, reloaded);

            PluginEntity entity = new PluginEntity();
            PluginEntity updated = service.update("p1", entity);

            assertThat(entity.getId()).isEqualTo("p1");
            assertThat(updated).isSameAs(reloaded);
            verify(pluginMapper).updateById(entity);
        }
    }

    @Nested
    @DisplayName("delete - 删除插件")
    class Delete {

        /**
         * 插件存在时执行删除
         */
        @Test
        void shouldDeleteExisting() {
            when(pluginMapper.selectById("p1")).thenReturn(plugin("p1", "cn.x.Plugin", true));

            service.delete("p1");

            verify(pluginMapper).deleteById("p1");
        }

        /**
         * 插件不存在时静默返回，不执行删除
         */
        @Test
        void shouldIgnoreMissingPlugin() {
            when(pluginMapper.selectById("p404")).thenReturn(null);

            service.delete("p404");

            verify(pluginMapper, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("pageQuery - 分页查询")
    class PageQuery {

        /**
         * 分页参数为空时默认第 1 页、每页 10 条
         */
        @Test
        void shouldUseDefaultPageParams() {
            when(pluginMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            IPage<PluginEntity> page = service.pageQuery(null, null, null, null);

            assertThat(page.getCurrent()).isEqualTo(1L);
            assertThat(page.getSize()).isEqualTo(10L);
        }

        /**
         * 指定分页参数与关键字/类型过滤条件
         */
        @Test
        @SuppressWarnings("unchecked")
        void shouldApplyFiltersAndCustomPageParams() {
            when(pluginMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            IPage<PluginEntity> page = service.pageQuery("示例", "tool", 2, 5);

            assertThat(page.getCurrent()).isEqualTo(2L);
            assertThat(page.getSize()).isEqualTo(5L);

            ArgumentCaptor<Wrapper<PluginEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(pluginMapper).selectPage(any(), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("name").contains("type");
        }
    }

    @Nested
    @DisplayName("list - 列表查询")
    class ListQuery {

        /**
         * 无过滤条件时直接返回全量列表
         */
        @Test
        void shouldReturnAllWhenNoFilter() {
            List<PluginEntity> plugins = List.of(plugin("p1", "cn.x.Plugin", true));
            when(pluginMapper.selectList(any())).thenReturn(plugins);

            assertThat(service.list((String) null, (String) null)).isEqualTo(plugins);
        }

        /**
         * 关键字与类型条件写入查询包装器
         */
        @Test
        @SuppressWarnings("unchecked")
        void shouldApplyFilters() {
            when(pluginMapper.selectList(any())).thenReturn(List.of());

            service.list("示例", "parser");

            ArgumentCaptor<Wrapper<PluginEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(pluginMapper).selectList(captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("name").contains("type");
        }
    }

    @Nested
    @DisplayName("reload - 重新加载插件")
    class Reload {

        /**
         * 插件不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingPlugin() {
            when(pluginMapper.selectById("p404")).thenReturn(null);

            assertThatThrownBy(() -> service.reload("p404"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("插件不存在");
            verifyNoInteractions(pluginRegistry);
        }

        /**
         * 未配置实现类时标记加载失败并落库
         */
        @Test
        void shouldMarkFailedWhenClassNameBlank() {
            PluginEntity entity = plugin("p1", "   ", true);
            when(pluginMapper.selectById("p1")).thenReturn(entity);

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isFalse();
            assertThat(result.getLoadError()).isEqualTo("未配置实现类");
            verify(pluginMapper).updateById(entity);
            verifyNoInteractions(pluginRegistry);
        }

        /**
         * 实现类找不到时记录异常信息
         */
        @Test
        void shouldMarkFailedWhenClassNotFound() {
            String className = "cn.cangjiecloud.missing.NoSuchPlugin";
            PluginEntity entity = plugin("p1", className, false);
            when(pluginMapper.selectById("p1")).thenReturn(entity);

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isFalse();
            assertThat(result.getLoadError()).contains(className);
            verify(pluginMapper).updateById(entity);
        }

        /**
         * 实现类未实现 Plugin 接口时给出明确错误
         */
        @Test
        void shouldMarkFailedWhenNotPlugin() {
            PluginEntity entity = plugin("p1", NotAPlugin.class.getName(), false);
            when(pluginMapper.selectById("p1")).thenReturn(entity);

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isFalse();
            assertThat(result.getLoadError()).isEqualTo("类未实现 Plugin 接口: " + NotAPlugin.class.getName());
        }

        /**
         * Spring 容器中存在 Bean 时直接注册该实例
         */
        @Test
        void shouldReloadFromSpringBean() {
            SamplePlugin plugin = new SamplePlugin();
            PluginEntity entity = plugin("p1", SamplePlugin.class.getName(), false);
            when(pluginMapper.selectById("p1")).thenReturn(entity);
            when(applicationContext.getBean(SamplePlugin.class)).thenReturn(plugin);

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isTrue();
            assertThat(result.getLoadError()).isNull();
            verify(pluginRegistry).register(plugin);
            verify(pluginMapper).updateById(entity);
        }

        /**
         * 容器中不存在 Bean 时反射实例化并注册
         */
        @Test
        void shouldReloadByReflectionWhenBeanMissing() {
            PluginEntity entity = plugin("p1", SamplePlugin.class.getName(), false);
            when(pluginMapper.selectById("p1")).thenReturn(entity);
            when(applicationContext.getBean(SamplePlugin.class))
                    .thenThrow(new NoSuchBeanDefinitionException(SamplePlugin.class));

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isTrue();
            verify(pluginRegistry).register(any(SamplePlugin.class));
        }

        /**
         * 注册中心抛异常时记录加载失败
         */
        @Test
        void shouldMarkFailedWhenRegisterThrows() {
            PluginEntity entity = plugin("p1", SamplePlugin.class.getName(), false);
            when(pluginMapper.selectById("p1")).thenReturn(entity);
            when(applicationContext.getBean(SamplePlugin.class)).thenReturn(new SamplePlugin());
            doThrow(new IllegalStateException("注册中心不可用")).when(pluginRegistry).register(any(SamplePlugin.class));

            PluginEntity result = service.reload("p1");

            assertThat(result.getLoaded()).isFalse();
            assertThat(result.getLoadError()).isEqualTo("注册中心不可用");
        }
    }

    @Nested
    @DisplayName("scanPlugins - 扫描注册中心插件")
    class ScanPlugins {

        /**
         * 新发现的插件按类名新增落库，并补齐默认状态与版本
         */
        @Test
        void shouldInsertNewDiscoveredPlugin() {
            when(pluginRegistry.list()).thenReturn(List.of(new SamplePlugin()));
            when(pluginMapper.selectList(any())).thenReturn(List.of());

            service.scanPlugins();

            ArgumentCaptor<PluginEntity> captor = ArgumentCaptor.forClass(PluginEntity.class);
            verify(pluginMapper).insertOrUpdate(captor.capture());
            PluginEntity saved = captor.getValue();
            assertThat(saved.getClassName()).isEqualTo(SamplePlugin.class.getName());
            assertThat(saved.getName()).isEqualTo("sample");
            assertThat(saved.getType()).isEqualTo("tool");
            assertThat(saved.getDescription()).isEqualTo("示例插件");
            assertThat(saved.getStatus()).isEqualTo("active");
            assertThat(saved.getLoaded()).isTrue();
            assertThat(saved.getLoadError()).isNull();
            assertThat(saved.getVersion()).isEqualTo("1.0.0");
            verify(pluginMapper, never()).updateById(any(PluginEntity.class));
        }

        /**
         * 已登记的插件按类名增量更新，且不覆盖已有版本号
         */
        @Test
        void shouldUpdateExistingPluginByClassName() {
            PluginEntity existing = plugin("p1", SamplePlugin.class.getName(), false);
            existing.setVersion("2.0.0");
            when(pluginRegistry.list()).thenReturn(List.of(new SamplePlugin()));
            when(pluginMapper.selectList(any())).thenReturn(List.of(existing));

            List<PluginEntity> result = service.scanPlugins();

            assertThat(existing.getLoaded()).isTrue();
            assertThat(existing.getLoadError()).isNull();
            assertThat(existing.getVersion()).isEqualTo("2.0.0");
            assertThat(result).containsExactly(existing);
            verify(pluginMapper).insertOrUpdate(existing);
            verify(pluginMapper, never()).insert(any(PluginEntity.class));
        }

        /**
         * 注册中心已消失的插件标记为未加载并记录原因
         */
        @Test
        void shouldMarkStalePluginAsUnloaded() {
            PluginEntity stale = plugin("p9", "cn.cangjiecloud.gone.GonePlugin", true);
            when(pluginMapper.selectList(any())).thenReturn(List.of(stale));
            when(pluginRegistry.list()).thenReturn(List.of());

            service.scanPlugins();

            assertThat(stale.getLoaded()).isFalse();
            assertThat(stale.getLoadError()).isEqualTo("扫描未发现该插件，可能已被移除");
            verify(pluginMapper).updateById(stale);
        }

        /**
         * 本就是未加载状态的历史记录不再重复更新
         */
        @Test
        void shouldNotTouchAlreadyUnloadedStalePlugin() {
            PluginEntity stale = plugin("p9", "cn.cangjiecloud.gone.GonePlugin", false);
            when(pluginMapper.selectList(any())).thenReturn(List.of(stale));
            when(pluginRegistry.list()).thenReturn(List.of());

            service.scanPlugins();

            verify(pluginMapper, never()).updateById(any(PluginEntity.class));
            verify(pluginMapper, never()).insert(any(PluginEntity.class));
        }
    }
}
