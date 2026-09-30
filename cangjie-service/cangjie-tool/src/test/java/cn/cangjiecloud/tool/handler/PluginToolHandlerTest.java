package cn.cangjiecloud.tool.handler;

import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.tool.ToolSpecification;
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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link PluginToolHandler} 单元测试：覆盖 Plugin 反射加载（Spring Bean 优先、
 * 容器缺失时反射实例化）、非 Plugin 实现类、类不存在、插件抛异常等路径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Plugin 工具处理器 - PluginToolHandler")
class PluginToolHandlerTest {

    @Mock
    private ApplicationContext applicationContext;

    private PluginToolHandler handler;

    /** 可反射实例化的测试插件 */
    public static class EchoPlugin implements Plugin {

        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "回声插件";
        }

        @Override
        public String getType() {
            return "tool";
        }

        @Override
        public Object execute(PluginContext context) {
            return "echo:" + context.getParams().get("text");
        }
    }

    /** 执行时抛异常的测试插件 */
    public static class BrokenPlugin implements Plugin {

        @Override
        public String getName() {
            return "broken";
        }

        @Override
        public String getDescription() {
            return "故障插件";
        }

        @Override
        public String getType() {
            return "tool";
        }

        @Override
        public Object execute(PluginContext context) {
            throw new IllegalStateException("插件内部错误");
        }
    }

    @BeforeEach
    void setUp() {
        handler = new PluginToolHandler(applicationContext);
    }

    private static ToolEntity tool(String implementation) {
        ToolEntity entity = new ToolEntity();
        entity.setId("t1");
        entity.setName("回声工具");
        entity.setToolType(ToolConstants.ToolType.PLUGIN);
        entity.setImplementation(implementation);
        entity.setParameters("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}");
        return entity;
    }

    @Nested
    @DisplayName("buildToolSpecification")
    class BuildSpecification {

        @Test
        @DisplayName("解析 functionName、描述与参数 Schema")
        void shouldBuildSpecification() {
            ToolEntity entity = tool(EchoPlugin.class.getName());
            entity.setFunctionName("echo_tool");
            entity.setDescription("回声");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getToolId()).isEqualTo("t1");
            assertThat(spec.getName()).isEqualTo("echo_tool");
            assertThat(spec.getDescription()).isEqualTo("回声");
            assertThat(spec.getToolType()).isEqualTo(ToolConstants.ToolType.PLUGIN);
            assertThat(spec.getParameters()).containsEntry("type", "object");
        }

        @Test
        @DisplayName("描述为空时回退工具名，参数非法 JSON 时返回空 Map")
        void shouldFallbackDescriptionAndToleratBadParameters() {
            ToolEntity entity = tool(EchoPlugin.class.getName());
            entity.setParameters("不是 JSON");

            ToolSpecification spec = handler.buildToolSpecification(entity);

            assertThat(spec.getDescription()).isEqualTo("回声工具");
            assertThat(spec.getParameters()).isEmpty();
            assertThat(spec.getName()).isEqualTo("tool_t1");
        }
    }

    @Nested
    @DisplayName("execute 执行")
    class Execute {

        @Test
        @DisplayName("Spring 容器中存在 Bean 时直接使用该 Bean")
        void shouldUseSpringBean() {
            EchoPlugin bean = new EchoPlugin();
            when(applicationContext.getBean(EchoPlugin.class)).thenReturn(bean);

            ToolExecuteResultDTO result = handler.execute(tool(EchoPlugin.class.getName()), Map.of("text", "hi"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("echo:hi");
            assertThat(result.getExecutionTime()).isNotNull();
        }

        @Test
        @DisplayName("容器中不存在 Bean 时反射实例化")
        void shouldInstantiateReflectivelyWhenBeanMissing() {
            when(applicationContext.getBean(EchoPlugin.class))
                    .thenThrow(new NoSuchBeanDefinitionException("没有该 Bean"));

            ToolExecuteResultDTO result = handler.execute(tool(EchoPlugin.class.getName()), Map.of("text", "fallback"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("echo:fallback");
        }

        @Test
        @DisplayName("插件执行抛异常时返回失败结果并带上异常信息")
        void shouldReturnFailureWhenPluginThrows() {
            when(applicationContext.getBean(BrokenPlugin.class)).thenReturn(new BrokenPlugin());

            ToolExecuteResultDTO result = handler.execute(tool(BrokenPlugin.class.getName()), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("插件内部错误");
        }

        @Test
        @DisplayName("未配置实现类时返回失败结果")
        void shouldFailWhenImplementationMissing() {
            ToolExecuteResultDTO result = handler.execute(tool(null), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("工具未配置实现类");
        }

        @Test
        @DisplayName("实现类未实现 Plugin 接口时返回失败结果")
        void shouldFailWhenClassIsNotPlugin() {
            ToolExecuteResultDTO result = handler.execute(tool("java.lang.String"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("实现类未实现 Plugin 接口");
        }

        @Test
        @DisplayName("实现类不存在时返回加载失败")
        void shouldFailWhenClassNotFound() {
            ToolExecuteResultDTO result = handler.execute(tool("com.not.exists.FooPlugin"), Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("加载插件失败");
        }

        @Test
        @DisplayName("metadata 中回传 toolId 与 toolName")
        void shouldPassMetadataToPlugin() {
            ToolEntity entity = tool(EchoPlugin.class.getName());
            entity.setId("tool-42");
            entity.setName("名字");
            when(applicationContext.getBean(EchoPlugin.class)).thenReturn(new EchoPlugin());

            ToolExecuteResultDTO result = handler.execute(entity, Map.of("text", "x"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("echo:x");
        }
    }
}
