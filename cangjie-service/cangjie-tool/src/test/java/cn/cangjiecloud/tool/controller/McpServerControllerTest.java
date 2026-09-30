package cn.cangjiecloud.tool.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.entity.McpServerEntity;
import cn.cangjiecloud.tool.service.IMcpServerService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link McpServerController} 单元测试：直接实例化控制器并 mock 服务层，不加载 Spring 上下文，
 * 验证注册、连通性测试、工具同步与删除等端点的委托与包装。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MCP 服务控制器 - McpServerController")
class McpServerControllerTest {

    @Mock
    private IMcpServerService mcpServerService;

    private McpServerController controller;

    @BeforeEach
    void setUp() {
        controller = new McpServerController(mcpServerService);
    }

    /**
     * 构造 MCP 服务实体
     */
    private static McpServerEntity server(String id) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(id);
        entity.setName("服务-" + id);
        entity.setServerUrl("http://localhost:3000/mcp");
        return entity;
    }

    @Nested
    @DisplayName("注册与删除")
    class RegisterAndDelete {

        /**
         * 注册：请求体透传并返回落库实体
         */
        @Test
        void registerShouldDelegateAndWrapEntity() {
            McpServerEntity entity = server(null);
            McpServerEntity registered = server("m1");
            when(mcpServerService.register(entity)).thenReturn(registered);

            R<McpServerEntity> response = controller.register(entity);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(registered);
            verify(mcpServerService).register(entity);
        }

        /**
         * 注册：服务层校验失败（重名等）时异常向上传播
         */
        @Test
        void registerShouldPropagateServiceException() {
            McpServerEntity entity = server(null);
            when(mcpServerService.register(entity)).thenThrow(new ApiException("服务名称已存在"));

            assertThatThrownBy(() -> controller.register(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("服务名称已存在");
        }

        /**
         * 删除：委托 deleteServer 后返回成功码
         */
        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("m1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(mcpServerService).deleteServer("m1");
        }
    }

    @Nested
    @DisplayName("连通性测试与工具同步")
    class TestAndSync {

        /**
         * 连通性测试：返回回写检查结果后的实体
         */
        @Test
        void testShouldReturnCheckedEntity() {
            McpServerEntity checked = server("m1");
            checked.setLastCheckResult("success");
            when(mcpServerService.testConnection("m1")).thenReturn(checked);

            R<McpServerEntity> response = controller.test("m1");

            assertThat(response.getData().getLastCheckResult()).isEqualTo("success");
            verify(mcpServerService).testConnection("m1");
        }

        /**
         * 工具同步：同步出的工具列表原样包装返回
         */
        @Test
        void syncShouldReturnToolList() {
            List<Map<String, Object>> tools = List.of(
                    Map.of("name", "weather", "description", "查询天气"),
                    Map.of("name", "search", "description", "搜索"));
            when(mcpServerService.syncTools("m1")).thenReturn(tools);

            R<List<Map<String, Object>>> response = controller.sync("m1");

            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData().get(0)).containsEntry("name", "weather");
            verify(mcpServerService).syncTools("m1");
        }

        /**
         * 工具同步：服务未发现任何工具时返回空列表
         */
        @Test
        void syncShouldReturnEmptyList() {
            when(mcpServerService.syncTools("m1")).thenReturn(List.of());

            R<List<Map<String, Object>>> response = controller.sync("m1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("分页查询")
    class ListQuery {

        /**
         * 列表：关键字与分页参数透传，Page 转 PageResult
         */
        @Test
        void listShouldConvertPageToPageResult() {
            Page<McpServerEntity> page = new Page<>(2, 20);
            page.setRecords(List.of(server("m1")));
            page.setTotal(42);
            when(mcpServerService.pageServers("kw", 2, 20)).thenReturn(page);

            R<PageResult<McpServerEntity>> response = controller.list("kw", 2, 20);

            PageResult<McpServerEntity> data = response.getData();
            assertThat(data.getList()).hasSize(1);
            assertThat(data.getTotal()).isEqualTo(42);
            assertThat(data.getPage()).isEqualTo(2);
            assertThat(data.getSize()).isEqualTo(20);
            verify(mcpServerService).pageServers("kw", 2, 20);
        }

        /**
         * 列表：关键字为空、分页取默认值 1/10 时原样透传
         */
        @Test
        void listShouldPassThroughNullKeywordAndDefaults() {
            when(mcpServerService.pageServers(null, 1, 10)).thenReturn(new Page<>(1, 10));

            R<PageResult<McpServerEntity>> response = controller.list(null, 1, 10);

            assertThat(response.getData().getList()).isEmpty();
            verify(mcpServerService).pageServers(null, 1, 10);
        }

        /**
         * 列表：服务层异常不返回包装结果
         */
        @Test
        void listShouldPropagateServiceException() {
            when(mcpServerService.pageServers(any(), any(), any()))
                    .thenThrow(new ApiException("查询失败"));

            assertThatThrownBy(() -> controller.list(null, 1, 10))
                    .isInstanceOf(ApiException.class);
        }
    }
}
