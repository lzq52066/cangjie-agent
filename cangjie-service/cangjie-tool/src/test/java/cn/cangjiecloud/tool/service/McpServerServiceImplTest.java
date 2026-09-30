package cn.cangjiecloud.tool.service;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.tool.entity.McpServerEntity;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.mapper.McpServerMapper;
import cn.cangjiecloud.tool.mcp.McpClientManager;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpServerServiceImpl 单元测试
 * <p>
 * 通过 mock 的 McpServerMapper / McpClientManager / IToolService 拦截数据库与网络访问。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MCP 服务实现 - McpServerServiceImpl")
class McpServerServiceImplTest {

    private static final String SERVER_URL = "http://127.0.0.1:9999/mcp";

    @Mock
    private McpClientManager mcpClientManager;
    @Mock
    private IToolService toolService;
    @Mock
    private McpServerMapper mcpServerMapper;

    private McpServerServiceImpl service;

    /**
     * 初始化 McpServerEntity 的 MyBatis-Plus 元数据，使 LambdaQueryWrapper 可以解析列名
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.tool.mapper.McpServerMapper");
        TableInfoHelper.initTableInfo(assistant, McpServerEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new McpServerServiceImpl(mcpClientManager, toolService);
        ReflectionTestUtils.setField(service, "baseMapper", mcpServerMapper);
    }

    /**
     * 构造 MCP 服务实体
     */
    private static McpServerEntity server(String id, String name, String url) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setServerUrl(url);
        return entity;
    }

    /**
     * 构造归属某个 MCP 服务的工具实体
     */
    private static ToolEntity mcpTool(String id, String functionName, String serverUrl) {
        ToolEntity tool = new ToolEntity();
        tool.setId(id);
        tool.setName(functionName);
        tool.setToolType("MCP");
        tool.setFunctionName(functionName);
        tool.setStatus("active");
        tool.setConfig(serverUrl == null ? null : JsonUtils.toJSONString(Map.of("serverUrl", serverUrl)));
        return tool;
    }

    @Nested
    @DisplayName("register - 注册 MCP 服务")
    class Register {

        /**
         * 服务地址为空时拒绝注册
         */
        @Test
        void shouldRejectBlankServerUrl() {
            McpServerEntity entity = server(null, "本地服务", "  ");

            assertThatThrownBy(() -> service.register(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 服务地址不能为空");
            verify(mcpServerMapper, never()).insert(any(McpServerEntity.class));
        }

        /**
         * 服务名称为空时拒绝注册
         */
        @Test
        void shouldRejectBlankName() {
            McpServerEntity entity = server(null, null, SERVER_URL);

            assertThatThrownBy(() -> service.register(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 服务名称不能为空");
        }

        /**
         * 相同地址重复注册时提示已存在
         */
        @Test
        void shouldRejectDuplicatedUrl() {
            McpServerEntity existing = server("m1", "已注册服务", SERVER_URL);
            when(mcpServerMapper.selectOne(any(), eq(true))).thenReturn(existing);

            assertThatThrownBy(() -> service.register(server(null, "新服务", SERVER_URL)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("该 MCP 服务已注册: 已注册服务");
            verify(mcpServerMapper, never()).insert(any(McpServerEntity.class));
        }

        /**
         * 正常注册：补齐默认状态与工具计数后落库
         */
        @Test
        void shouldRegisterWithDefaultStatusAndToolCount() {
            when(mcpServerMapper.selectOne(any(), eq(true))).thenReturn(null);
            McpServerEntity entity = server(null, "本地服务", SERVER_URL);

            McpServerEntity registered = service.register(entity);

            assertThat(registered).isSameAs(entity);
            assertThat(registered.getStatus()).isEqualTo("active");
            assertThat(registered.getToolCount()).isZero();
            verify(mcpServerMapper).insert(entity);
        }

        /**
         * 显式传入的状态不被覆盖
         */
        @Test
        void shouldKeepProvidedStatus() {
            when(mcpServerMapper.selectOne(any(), eq(true))).thenReturn(null);
            McpServerEntity entity = server(null, "本地服务", SERVER_URL);
            entity.setStatus("inactive");

            service.register(entity);

            assertThat(entity.getStatus()).isEqualTo("inactive");
        }
    }

    @Nested
    @DisplayName("pageServers - 分页查询")
    class PageServers {

        /**
         * 分页参数为空时默认第 1 页、每页 10 条
         */
        @Test
        void shouldUseDefaultPageParams() {
            when(mcpServerMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            IPage<McpServerEntity> page = service.pageServers(null, null, null);

            assertThat(page.getCurrent()).isEqualTo(1L);
            assertThat(page.getSize()).isEqualTo(10L);
        }

        /**
         * 关键字过滤写入查询包装器
         */
        @Test
        @SuppressWarnings("unchecked")
        void shouldApplyKeywordFilter() {
            when(mcpServerMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.pageServers("本地", 3, 20);

            ArgumentCaptor<Wrapper<McpServerEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(mcpServerMapper).selectPage(any(), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("name");
        }
    }

    @Nested
    @DisplayName("testConnection - 连通性测试")
    class TestConnection {

        /**
         * 服务不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingServer() {
            when(mcpServerMapper.selectById("m404")).thenReturn(null);

            assertThatThrownBy(() -> service.testConnection("m404"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 服务不存在");
        }

        /**
         * 握手成功时记录 success 与检查时间
         */
        @Test
        void shouldMarkSuccess() {
            McpServerEntity entity = server("m1", "本地服务", SERVER_URL);
            when(mcpServerMapper.selectById("m1")).thenReturn(entity);
            when(mcpClientManager.testConnection(SERVER_URL)).thenReturn(true);

            McpServerEntity result = service.testConnection("m1");

            assertThat(result.getLastCheckResult()).isEqualTo("success");
            assertThat(result.getLastCheckTime()).isNotNull();
            verify(mcpServerMapper).updateById(entity);
        }

        /**
         * 握手失败时记录 failed
         */
        @Test
        void shouldMarkFailed() {
            McpServerEntity entity = server("m1", "本地服务", SERVER_URL);
            when(mcpServerMapper.selectById("m1")).thenReturn(entity);
            when(mcpClientManager.testConnection(SERVER_URL)).thenReturn(false);

            assertThat(service.testConnection("m1").getLastCheckResult()).isEqualTo("failed");
        }

        /**
         * 客户端抛异常时按失败处理，不向外扩散
         */
        @Test
        void shouldMarkFailedWhenClientThrows() {
            McpServerEntity entity = server("m1", "本地服务", SERVER_URL);
            when(mcpServerMapper.selectById("m1")).thenReturn(entity);
            when(mcpClientManager.testConnection(SERVER_URL)).thenThrow(new RuntimeException("连接超时"));

            assertThat(service.testConnection("m1").getLastCheckResult()).isEqualTo("failed");
            verify(mcpServerMapper).updateById(entity);
        }
    }

    @Nested
    @DisplayName("syncTools - 同步工具")
    class SyncTools {

        /**
         * 服务不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingServer() {
            when(mcpServerMapper.selectById("m404")).thenReturn(null);

            assertThatThrownBy(() -> service.syncTools("m404"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 服务不存在");
        }

        /**
         * 工具发现失败时包装为业务异常
         */
        @Test
        void shouldWrapDiscoveryFailure() {
            when(mcpServerMapper.selectById("m1")).thenReturn(server("m1", "本地服务", SERVER_URL));
            when(mcpClientManager.listTools(SERVER_URL, true)).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> service.syncTools("m1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 工具发现失败: boom");
        }

        /**
         * 新工具落库：无名称的条目跳过，描述与参数按默认值补齐
         */
        @Test
        void shouldInsertNewToolsAndSkipUnnamed() {
            McpServerEntity entity = server("m1", "本地服务", SERVER_URL);
            when(mcpServerMapper.selectById("m1")).thenReturn(entity);
            Map<String, Object> withSchema = new HashMap<>();
            withSchema.put("name", "tool_a");
            withSchema.put("description", "描述A");
            withSchema.put("inputSchema", Map.of("type", "object"));
            Map<String, Object> unnamedOnly = new HashMap<>();
            unnamedOnly.put("description", "没有名字");
            when(mcpClientManager.listTools(SERVER_URL, true)).thenReturn(List.of(
                    withSchema,
                    Map.of("name", "tool_b"),
                    unnamedOnly));
            when(toolService.list((Wrapper<ToolEntity>) any())).thenReturn(List.of());

            List<Map<String, Object>> result = service.syncTools("m1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("name", "tool_a").containsEntry("isNew", true);
            assertThat(result.get(1)).containsEntry("name", "tool_b").containsEntry("isNew", true);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService, times(2)).saveOrUpdate(captor.capture());
            ToolEntity toolA = captor.getAllValues().get(0);
            assertThat(toolA.getType()).isEqualTo("function");
            assertThat(toolA.getToolType()).isEqualTo("MCP");
            assertThat(toolA.getFunctionName()).isEqualTo("tool_a");
            assertThat(toolA.getDescription()).isEqualTo("描述A");
            assertThat(toolA.getParameters()).isEqualTo(JsonUtils.toJSONString(Map.of("type", "object")));
            assertThat(toolA.getConfig()).contains(SERVER_URL);
            assertThat(toolA.getCategory()).isEqualTo("MCP:本地服务");
            assertThat(toolA.getStatus()).isEqualTo("active");

            ToolEntity toolB = captor.getAllValues().get(1);
            assertThat(toolB.getDescription()).isEqualTo("tool_b");
            assertThat(toolB.getParameters()).isEqualTo("{}");

            assertThat(entity.getToolCount()).isEqualTo(2);
            assertThat(entity.getLastCheckResult()).isEqualTo("success");
            assertThat(entity.getLastCheckTime()).isNotNull();
            verify(mcpServerMapper).updateById(entity);
        }

        /**
         * 函数名与其它工具冲突时落库但不激活
         */
        @Test
        void shouldDeactivateConflictingTool() {
            when(mcpServerMapper.selectById("m1")).thenReturn(server("m1", "本地服务", SERVER_URL));
            when(mcpClientManager.listTools(SERVER_URL, true)).thenReturn(List.of(Map.of("name", "dup_tool")));
            when(toolService.list((Wrapper<ToolEntity>) any())).thenReturn(List.of());
            when(toolService.count(any())).thenReturn(1L);

            List<Map<String, Object>> result = service.syncTools("m1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("name", "dup_tool")
                    .containsEntry("isNew", true)
                    .containsEntry("conflict", true);

            ArgumentCaptor<ToolEntity> captor = ArgumentCaptor.forClass(ToolEntity.class);
            verify(toolService).saveOrUpdate(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("inactive");
        }

        /**
         * 已同步的同名工具原位更新，不产生新记录
         */
        @Test
        void shouldUpdateExistingToolInPlace() {
            when(mcpServerMapper.selectById("m1")).thenReturn(server("m1", "本地服务", SERVER_URL));
            when(mcpClientManager.listTools(SERVER_URL, true)).thenReturn(List.of(Map.of("name", "tool_a")));
            ToolEntity existing = mcpTool("t9", "tool_a", SERVER_URL);
            when(toolService.list((Wrapper<ToolEntity>) any())).thenReturn(List.of(existing));

            List<Map<String, Object>> result = service.syncTools("m1");

            assertThat(result.get(0)).containsEntry("isNew", false);
            verify(toolService).saveOrUpdate(existing);
            assertThat(existing.getStatus()).isEqualTo("active");
        }

        /**
         * 服务端已移除的工具置为失效，其它服务的同名工具不受影响
         */
        @Test
        void shouldDeactivateRemovedToolsOnlyOfThisServer() {
            when(mcpServerMapper.selectById("m1")).thenReturn(server("m1", "本地服务", SERVER_URL));
            when(mcpClientManager.listTools(SERVER_URL, true)).thenReturn(List.of(Map.of("name", "tool_a")));
            ToolEntity removed = mcpTool("t8", "gone_tool", SERVER_URL);
            ToolEntity otherServer = mcpTool("t7", "other_tool", "http://127.0.0.1:8888/mcp");
            ToolEntity brokenConfig = mcpTool("t6", "broken_tool", null);
            brokenConfig.setConfig("{不是合法 JSON");
            when(toolService.list((Wrapper<ToolEntity>) any())).thenReturn(List.of(removed, otherServer, brokenConfig));

            service.syncTools("m1");

            assertThat(removed.getStatus()).isEqualTo("inactive");
            verify(toolService).updateById(removed);
            assertThat(otherServer.getStatus()).isEqualTo("active");
            assertThat(brokenConfig.getStatus()).isEqualTo("active");
            verify(toolService, never()).updateById(otherServer);
            verify(toolService, never()).updateById(brokenConfig);
        }
    }

    @Nested
    @DisplayName("deleteServer - 删除 MCP 服务")
    class DeleteServer {

        /**
         * 服务不存在时抛出业务异常
         */
        @Test
        void shouldRejectMissingServer() {
            when(mcpServerMapper.selectById("m404")).thenReturn(null);

            assertThatThrownBy(() -> service.deleteServer("m404"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("MCP 服务不存在");
        }

        /**
         * 删除时失效本服务同步的工具、清理会话并移除记录
         */
        @Test
        void shouldDeactivateToolsAndEvictSession() {
            McpServerEntity entity = server("m1", "本地服务", SERVER_URL);
            when(mcpServerMapper.selectById("m1")).thenReturn(entity);
            ToolEntity mine = mcpTool("t8", "gone_tool", SERVER_URL);
            ToolEntity other = mcpTool("t7", "other_tool", "http://127.0.0.1:8888/mcp");
            when(toolService.list((Wrapper<ToolEntity>) any())).thenReturn(List.of(mine, other));

            service.deleteServer("m1");

            assertThat(mine.getStatus()).isEqualTo("inactive");
            assertThat(other.getStatus()).isEqualTo("active");
            verify(toolService).updateById(mine);
            verify(toolService, never()).updateById(other);
            verify(mcpClientManager).evictSession(SERVER_URL);
            verify(mcpServerMapper).deleteById("m1");
        }
    }
}
