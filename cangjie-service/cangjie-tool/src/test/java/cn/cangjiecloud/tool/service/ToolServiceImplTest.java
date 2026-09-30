package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.plugin.Plugin;
import cn.cangjiecloud.core.plugin.PluginContext;
import cn.cangjiecloud.core.tool.ToolSpecification;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.consts.ToolConstants;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.handler.AbsToolHandler;
import cn.cangjiecloud.tool.handler.SkillToolHandler;
import cn.cangjiecloud.tool.handler.ToolHandlerRegistry;
import cn.cangjiecloud.tool.mapper.ToolMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ToolServiceImpl 单元测试
 * <p>
 * 通过注入 mock 的 ToolMapper 拦截全部数据库访问，不启动 Spring 上下文、不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工具服务实现 - ToolServiceImpl")
class ToolServiceImplTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ToolHandlerRegistry handlerRegistry;
    @Mock
    private ToolMapper toolMapper;
    @Mock
    private AbsToolHandler customHandler;
    @Mock
    private SkillToolHandler skillToolHandler;

    private ToolServiceImpl service;

    /**
     * 初始化 ToolEntity 的 MyBatis-Plus 元数据，使 LambdaQueryWrapper 可以解析列名
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.tool.mapper.ToolMapper");
        TableInfoHelper.initTableInfo(assistant, ToolEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ToolServiceImpl(applicationContext, handlerRegistry);
        ReflectionTestUtils.setField(service, "baseMapper", toolMapper);
    }

    /**
     * 构造工具实体
     */
    private static ToolEntity tool(String id, String toolType, String status) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setToolType(toolType);
        entity.setStatus(status);
        return entity;
    }

    /**
     * 回显测试插件：作为 Spring Bean 或反射实例化的目标
     */
    public static class EchoPlugin implements Plugin {
        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "回显插件";
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

    /**
     * 抛异常的测试插件
     */
    public static class BrokenPlugin implements Plugin {
        @Override
        public String getName() {
            return "broken";
        }

        @Override
        public String getDescription() {
            return "异常插件";
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

    @Nested
    @DisplayName("create - 创建工具")
    class Create {

        /**
         * 未传状态时默认 active 并落库
         */
        @Test
        void shouldCreateWithDefaultActiveStatus() {
            ToolEntity entity = new ToolEntity();
            entity.setName("新工具");

            ToolEntity created = service.create(entity);

            assertThat(created).isSameAs(entity);
            assertThat(created.getStatus()).isEqualTo(ToolConstants.STATUS_ACTIVE);
            verify(toolMapper).insert(entity);
            verify(toolMapper, never()).selectCount(any());
        }

        /**
         * functionName 首尾空白应被归一化后落库
         */
        @Test
        void shouldNormalizeFunctionName() {
            when(toolMapper.selectCount(any())).thenReturn(0L);
            ToolEntity entity = new ToolEntity();
            entity.setFunctionName("  my_tool  ");

            service.create(entity);

            assertThat(entity.getFunctionName()).isEqualTo("my_tool");
        }

        /**
         * 显式传入的状态不被默认值覆盖
         */
        @Test
        void shouldKeepProvidedStatus() {
            ToolEntity entity = new ToolEntity();
            entity.setStatus(ToolConstants.STATUS_INACTIVE);

            service.create(entity);

            assertThat(entity.getStatus()).isEqualTo(ToolConstants.STATUS_INACTIVE);
        }

        /**
         * 非法函数名（数字开头）应拒绝创建
         */
        @Test
        void shouldRejectInvalidFunctionName() {
            ToolEntity entity = new ToolEntity();
            entity.setFunctionName("1bad");

            assertThatThrownBy(() -> service.create(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("函数名只允许字母");
            verify(toolMapper, never()).insert(any(ToolEntity.class));
        }

        /**
         * 使用平台保留前缀或保留全名应拒绝创建
         */
        @Test
        void shouldRejectReservedFunctionName() {
            ToolEntity prefixed = new ToolEntity();
            prefixed.setFunctionName("tool_abc");
            assertThatThrownBy(() -> service.create(prefixed))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("与平台内置命名规则冲突");

            ToolEntity reserved = new ToolEntity();
            reserved.setFunctionName("search_knowledge_base");
            assertThatThrownBy(() -> service.create(reserved))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("与平台内置命名规则冲突");
            verify(toolMapper, never()).insert(any(ToolEntity.class));
        }

        /**
         * 函数名重复时应拒绝创建
         */
        @Test
        void shouldRejectDuplicatedFunctionName() {
            when(toolMapper.selectCount(any())).thenReturn(1L);
            ToolEntity entity = new ToolEntity();
            entity.setFunctionName("dup_tool");

            assertThatThrownBy(() -> service.create(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("函数名已存在: dup_tool");
            verify(toolMapper, never()).insert(any(ToolEntity.class));
        }
    }

    @Nested
    @DisplayName("update - 更新工具")
    class Update {

        /**
         * 工具不存在时抛出 ApiException
         */
        @Test
        void shouldThrowWhenToolNotFound() {
            when(toolMapper.selectById("t404")).thenReturn(null);

            assertThatThrownBy(() -> service.update("t404", new ToolEntity()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具不存在");
            verify(toolMapper, never()).updateById(any(ToolEntity.class));
        }

        /**
         * 更新成功后回读最新数据，且 id 强制使用路径参数
         */
        @Test
        void shouldUpdateAndReload() {
            ToolEntity existing = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            ToolEntity reloaded = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            reloaded.setName("更新后");
            when(toolMapper.selectById("t1")).thenReturn(existing, reloaded);

            ToolEntity toUpdate = new ToolEntity();
            toUpdate.setName("更新后");
            ToolEntity result = service.update("t1", toUpdate);

            assertThat(result).isSameAs(reloaded);
            assertThat(toUpdate.getId()).isEqualTo("t1");
            verify(toolMapper).updateById(toUpdate);
        }

        /**
         * 更新时函数名与其他工具重复应拒绝
         */
        @Test
        void shouldRejectDuplicatedFunctionNameOnUpdate() {
            when(toolMapper.selectById("t1")).thenReturn(tool("t1", null, ToolConstants.STATUS_ACTIVE));
            when(toolMapper.selectCount(any())).thenReturn(2L);
            ToolEntity toUpdate = new ToolEntity();
            toUpdate.setFunctionName("dup_tool");

            assertThatThrownBy(() -> service.update("t1", toUpdate))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("函数名已存在: dup_tool");
            verify(toolMapper, never()).updateById(any(ToolEntity.class));
        }
    }

    @Nested
    @DisplayName("delete - 删除工具")
    class Delete {

        /**
         * 工具存在时执行逻辑删除
         */
        @Test
        void shouldDeleteExisting() {
            when(toolMapper.selectById("t1")).thenReturn(tool("t1", null, ToolConstants.STATUS_ACTIVE));

            service.delete("t1");

            verify(toolMapper).deleteById("t1");
        }

        /**
         * 工具不存在时静默返回，不执行删除
         */
        @Test
        void shouldIgnoreMissingTool() {
            when(toolMapper.selectById("t404")).thenReturn(null);

            service.delete("t404");

            verify(toolMapper, never()).deleteById(any());
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
            when(toolMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            IPage<ToolEntity> page = service.pageQuery(null, null, null, null);

            assertThat(page.getCurrent()).isEqualTo(1L);
            assertThat(page.getSize()).isEqualTo(10L);
        }

        /**
         * 指定分页参数与关键字/类型过滤条件
         */
        @Test
        @SuppressWarnings("unchecked")
        void shouldApplyFiltersAndCustomPageParams() {
            when(toolMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            IPage<ToolEntity> page = service.pageQuery("计算", "HTTP", 2, 5);

            assertThat(page.getCurrent()).isEqualTo(2L);
            assertThat(page.getSize()).isEqualTo(5L);

            ArgumentCaptor<Wrapper<ToolEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(toolMapper).selectPage(any(), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("name").contains("type");
        }
    }

    @Nested
    @DisplayName("executeTool - 执行工具（策略分发）")
    class ExecuteTool {

        /**
         * 工具不存在时抛出异常
         */
        @Test
        void shouldThrowWhenToolNotFound() {
            when(toolMapper.selectById("t404")).thenReturn(null);

            assertThatThrownBy(() -> service.executeTool("t404", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具不存在");
        }

        /**
         * 工具未激活时抛出异常
         */
        @Test
        void shouldThrowWhenToolInactive() {
            when(toolMapper.selectById("t1"))
                    .thenReturn(tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_INACTIVE));

            assertThatThrownBy(() -> service.executeTool("t1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具未激活");
        }

        /**
         * 本地工具不在服务端执行，直接返回失败说明
         */
        @Test
        void shouldRejectLocalTool() {
            when(toolMapper.selectById("t1"))
                    .thenReturn(tool("t1", ToolConstants.ToolType.LOCAL, ToolConstants.STATUS_ACTIVE));

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("本地工具不在服务端执行");
            verifyNoInteractions(handlerRegistry);
        }

        /**
         * 命中处理器时直接委托执行
         */
        @Test
        void shouldDispatchToHandler() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(customHandler);
            ToolExecuteResultDTO dto = ToolExecuteResultDTO.builder().success(true).output("ok").build();
            when(customHandler.execute(entity, Map.of("a", 1))).thenReturn(dto);

            assertThat(service.executeTool("t1", Map.of("a", 1))).isSameAs(dto);
        }

        /**
         * 新 toolType 字段为空时按旧 type 字段映射（http -> HTTP）
         */
        @Test
        void shouldResolveLegacyTypeField() {
            ToolEntity entity = tool("t1", null, ToolConstants.STATUS_ACTIVE);
            entity.setType("http");
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.HTTP)).thenReturn(customHandler);
            ToolExecuteResultDTO dto = ToolExecuteResultDTO.builder().success(true).output("ok").build();
            when(customHandler.execute(entity, Map.of())).thenReturn(dto);

            assertThat(service.executeTool("t1", Map.of())).isSameAs(dto);
            verify(handlerRegistry).get(ToolConstants.ToolType.HTTP);
        }

        /**
         * 无处理器时兜底走旧版 Plugin：优先从 Spring 容器获取 Bean
         */
        @Test
        void shouldFallbackToLegacyPluginBean() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            entity.setImplementation(EchoPlugin.class.getName());
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(applicationContext.getBean(EchoPlugin.class)).thenReturn(new EchoPlugin());

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of("text", "hi"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("echo:hi");
        }

        /**
         * 容器中不存在 Bean 时反射实例化实现类
         */
        @Test
        void shouldFallbackToLegacyPluginByReflection() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            entity.setImplementation(EchoPlugin.class.getName());
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(applicationContext.getBean(EchoPlugin.class))
                    .thenThrow(new NoSuchBeanDefinitionException(EchoPlugin.class));

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of("text", "yo"));

            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("echo:yo");
        }

        /**
         * 旧版 Plugin 执行抛异常时返回失败结果
         */
        @Test
        void shouldReturnFailureWhenLegacyPluginThrows() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            entity.setImplementation(BrokenPlugin.class.getName());
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(applicationContext.getBean(BrokenPlugin.class))
                    .thenThrow(new NoSuchBeanDefinitionException(BrokenPlugin.class));

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("插件内部错误");
        }

        /**
         * 未配置实现类时给出明确错误
         */
        @Test
        void shouldRejectBlankImplementation() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("工具未配置实现类");
        }

        /**
         * 实现类未实现 Plugin 接口时给出明确错误
         */
        @Test
        void shouldRejectNonPluginImplementation() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            entity.setImplementation("java.lang.String");
            when(toolMapper.selectById("t1")).thenReturn(entity);

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("未实现 Plugin 接口");
        }

        /**
         * 实现类无法加载时给出明确错误
         */
        @Test
        void shouldRejectUnloadableImplementation() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.PLUGIN, ToolConstants.STATUS_ACTIVE);
            entity.setImplementation("com.not.exists.FooPlugin");
            when(toolMapper.selectById("t1")).thenReturn(entity);

            ToolExecuteResultDTO result = service.executeTool("t1", Map.of());

            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getError()).contains("加载插件失败");
        }
    }

    @Nested
    @DisplayName("getToolSpecifications - 批量构建工具规格")
    class GetToolSpecifications {

        /**
         * 入参为空时直接返回空列表，不访问数据库
         */
        @Test
        void shouldReturnEmptyForEmptyIds() {
            assertThat(service.getToolSpecifications(null)).isEmpty();
            assertThat(service.getToolSpecifications(List.of())).isEmpty();
            verifyNoInteractions(toolMapper, handlerRegistry);
        }

        /**
         * 数据库查不到工具时返回空列表
         */
        @Test
        void shouldReturnEmptyWhenToolsMissing() {
            when(toolMapper.selectByIds(anyList())).thenReturn(List.of());

            assertThat(service.getToolSpecifications(List.of("a", "b"))).isEmpty();
        }

        /**
         * 按类型分组构建：未激活工具被过滤，无处理器的类型走基础构建
         */
        @Test
        void shouldGroupByTypeAndFilterInactive() {
            ToolEntity active = tool("a", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            active.setFunctionName("my_fn");
            active.setParameters("{\"type\":\"object\"}");
            ToolEntity inactive = tool("b", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_INACTIVE);
            ToolEntity unknown = tool("c", "WEIRD", ToolConstants.STATUS_ACTIVE);
            unknown.setType("legacy_type");
            unknown.setName("未知工具");
            unknown.setParameters("{不是合法 JSON");

            when(toolMapper.selectByIds(anyList())).thenReturn(List.of(active, inactive, unknown));
            ToolSpecification customSpec = ToolSpecification.builder().name("my_fn").build();
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(customHandler);
            when(handlerRegistry.get("WEIRD")).thenReturn(null);
            when(customHandler.buildToolSpecifications(List.of(active))).thenReturn(List.of(customSpec));

            List<ToolSpecification> result = service.getToolSpecifications(List.of("a", "b", "c"));

            assertThat(result).extracting(ToolSpecification::getName)
                    .containsExactlyInAnyOrder("my_fn", "tool_c");
            verify(customHandler).buildToolSpecifications(List.of(active));

            ToolSpecification basic = result.stream()
                    .filter(spec -> "tool_c".equals(spec.getName()))
                    .findFirst().orElseThrow();
            assertThat(basic.getToolId()).isEqualTo("c");
            assertThat(basic.getToolType()).isEqualTo("legacy_type");
            assertThat(basic.getDescription()).isEqualTo("未知工具");
            assertThat(basic.getParameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveByCallName - 函数名解析")
    class ResolveByCallName {

        /**
         * 空白名称返回 null 且不访问数据库
         */
        @Test
        void shouldReturnNullForBlankName() {
            assertThat(service.resolveByCallName(null)).isNull();
            assertThat(service.resolveByCallName("   ")).isNull();
            verifyNoInteractions(toolMapper);
        }

        /**
         * 自定义函数名优先按 functionName 查询
         */
        @Test
        void shouldFindByCustomFunctionName() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectOne(any(), eq(false))).thenReturn(entity);

            assertThat(service.resolveByCallName("my_tool")).isSameAs(entity);
            verify(toolMapper, never()).selectById(any());
        }

        /**
         * 保留前缀命名（tool_xxx）跳过 functionName 查询，直接按 ID 回退
         */
        @Test
        void shouldFallbackToPrefixedId() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);

            assertThat(service.resolveByCallName("tool_t1")).isSameAs(entity);
            verify(toolMapper, never()).selectOne(any(), anyBoolean());
        }

        /**
         * skill_ 前缀命名同样按 ID 回退
         */
        @Test
        void shouldFallbackToSkillId() {
            ToolEntity entity = tool("s1", ToolConstants.ToolType.SKILL, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("s1")).thenReturn(entity);

            assertThat(service.resolveByCallName("skill_s1")).isSameAs(entity);
            verify(toolMapper, never()).selectOne(any(), anyBoolean());
        }

        /**
         * 自定义名与 ID 均查不到时返回 null
         */
        @Test
        void shouldReturnNullWhenNothingMatches() {
            when(toolMapper.selectOne(any(), eq(false))).thenReturn(null);
            when(toolMapper.selectById("ghost")).thenReturn(null);

            assertThat(service.resolveByCallName("ghost")).isNull();
            verify(toolMapper).selectOne(any(), eq(false));
            verify(toolMapper).selectById("ghost");
        }
    }

    @Nested
    @DisplayName("executeToolCall - 按函数名执行")
    class ExecuteToolCall {

        /**
         * skill_ 前缀且技能处理器缺失时返回错误 JSON
         */
        @Test
        void shouldFailWhenSkillHandlerMissing() {
            String result = service.executeToolCall("skill_s1", Map.of());

            Map<String, Object> json = JsonUtils.parseMap(result);
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("技能处理器未初始化");
        }

        /**
         * skill_ 前缀命中技能处理器时返回输出文本
         */
        @Test
        void shouldExecuteSkillCall() {
            ReflectionTestUtils.setField(service, "skillToolHandler", skillToolHandler);
            when(skillToolHandler.execute("s1", Map.of("q", "1")))
                    .thenReturn(ToolExecuteResultDTO.builder().success(true).output("skill-ok").build());

            assertThat(service.executeToolCall("skill_s1", Map.of("q", "1"))).isEqualTo("skill-ok");
        }

        /**
         * 技能执行失败时包装为错误 JSON
         */
        @Test
        void shouldWrapSkillFailure() {
            ReflectionTestUtils.setField(service, "skillToolHandler", skillToolHandler);
            when(skillToolHandler.execute("s1", Map.of()))
                    .thenReturn(ToolExecuteResultDTO.builder().success(false).error("技能炸了").build());

            Map<String, Object> json = JsonUtils.parseMap(service.executeToolCall("skill_s1", Map.of()));
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("技能炸了");
        }

        /**
         * 工具不存在时返回错误 JSON
         */
        @Test
        void shouldFailWhenToolNotFound() {
            when(toolMapper.selectOne(any(), eq(false))).thenReturn(null);
            when(toolMapper.selectById("ghost")).thenReturn(null);

            Map<String, Object> json = JsonUtils.parseMap(service.executeToolCall("ghost", Map.of()));
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("工具不存在");
        }

        /**
         * 本地工具返回不可服务端执行的错误 JSON
         */
        @Test
        void shouldFailForLocalTool() {
            when(toolMapper.selectById("t1"))
                    .thenReturn(tool("t1", ToolConstants.ToolType.LOCAL, ToolConstants.STATUS_ACTIVE));

            Map<String, Object> json = JsonUtils.parseMap(service.executeToolCall("tool_t1", Map.of()));
            assertThat(json.get("success")).isEqualTo(false);
            assertThat((String) json.get("error")).contains("本地工具不在服务端执行");
        }

        /**
         * 无对应处理器时返回不支持类型的错误 JSON
         */
        @Test
        void shouldFailForUnsupportedType() {
            when(toolMapper.selectById("t1"))
                    .thenReturn(tool("t1", "WEIRD", ToolConstants.STATUS_ACTIVE));

            Map<String, Object> json = JsonUtils.parseMap(service.executeToolCall("tool_t1", Map.of()));
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("不支持的工具类型");
        }

        /**
         * 执行成功时直接返回输出文本
         */
        @Test
        void shouldReturnOutputOnSuccess() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(customHandler);
            when(customHandler.execute(entity, Map.of("a", 1)))
                    .thenReturn(ToolExecuteResultDTO.builder().success(true).output("out").build());

            assertThat(service.executeToolCall("tool_t1", Map.of("a", 1))).isEqualTo("out");
        }

        /**
         * 输出为 null 时返回空字符串
         */
        @Test
        void shouldReturnEmptyStringWhenOutputNull() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(customHandler);
            when(customHandler.execute(entity, Map.of()))
                    .thenReturn(ToolExecuteResultDTO.builder().success(true).build());

            assertThat(service.executeToolCall("tool_t1", Map.of())).isEmpty();
        }

        /**
         * 执行失败时包装为错误 JSON
         */
        @Test
        void shouldWrapHandlerFailure() {
            ToolEntity entity = tool("t1", ToolConstants.ToolType.CUSTOM, ToolConstants.STATUS_ACTIVE);
            when(toolMapper.selectById("t1")).thenReturn(entity);
            when(handlerRegistry.get(ToolConstants.ToolType.CUSTOM)).thenReturn(customHandler);
            when(customHandler.execute(entity, Map.of()))
                    .thenReturn(ToolExecuteResultDTO.builder().success(false).error("handler 失败").build());

            Map<String, Object> json = JsonUtils.parseMap(service.executeToolCall("tool_t1", Map.of()));
            assertThat(json.get("success")).isEqualTo(false);
            assertThat(json.get("error")).isEqualTo("handler 失败");
        }
    }

    @Nested
    @DisplayName("getSkillSpecifications - 技能规格列表")
    class GetSkillSpecifications {

        /**
         * 入参为空或技能处理器缺失时返回空列表
         */
        @Test
        void shouldReturnEmptyWhenUnavailable() {
            assertThat(service.getSkillSpecifications(null)).isEmpty();
            assertThat(service.getSkillSpecifications(List.of())).isEmpty();
            assertThat(service.getSkillSpecifications(List.of("s1"))).isEmpty();
        }

        /**
         * 逐个构建并过滤 null 结果
         */
        @Test
        void shouldBuildAndFilterNullResults() {
            ReflectionTestUtils.setField(service, "skillToolHandler", skillToolHandler);
            ToolSpecification spec = ToolSpecification.builder().toolId("s1").name("skill_s1").build();
            when(skillToolHandler.buildToolSpecification("s1")).thenReturn(spec);
            when(skillToolHandler.buildToolSpecification("s2")).thenReturn(null);

            assertThat(service.getSkillSpecifications(List.of("s1", "s2"))).containsExactly(spec);
        }
    }
}
