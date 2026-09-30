package cn.cangjiecloud.tool.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.entity.PluginEntity;
import cn.cangjiecloud.tool.service.IPluginService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginController} 单元测试：直接实例化控制器并 mock 服务层，不加载 Spring 上下文，
 * 验证增删改查、reload / scan 等端点的委托与返回包装。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("插件控制器 - PluginController")
class PluginControllerTest {

    @Mock
    private IPluginService pluginService;

    private PluginController controller;

    @BeforeEach
    void setUp() {
        controller = new PluginController(pluginService);
    }

    /**
     * 构造插件实体
     */
    private static PluginEntity plugin(String id) {
        PluginEntity entity = new PluginEntity();
        entity.setId(id);
        entity.setName("插件-" + id);
        return entity;
    }

    @Nested
    @DisplayName("写操作")
    class WriteOperations {

        /**
         * 创建：委托服务并包装返回实体
         */
        @Test
        void createShouldDelegateAndWrapEntity() {
            PluginEntity entity = plugin(null);
            entity.setClassName("cn.demo.Plugin");
            PluginEntity created = plugin("p1");
            when(pluginService.create(entity)).thenReturn(created);

            R<PluginEntity> response = controller.create(entity);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(created);
            verify(pluginService).create(entity);
        }

        /**
         * 更新：路径 id 与请求体透传
         */
        @Test
        void updateShouldDelegateWithPathId() {
            PluginEntity entity = plugin(null);
            when(pluginService.update("p1", entity)).thenReturn(plugin("p1"));

            R<PluginEntity> response = controller.update("p1", entity);

            assertThat(response.getData().getId()).isEqualTo("p1");
            verify(pluginService).update("p1", entity);
        }

        /**
         * 删除：委托后返回成功码且 data 为空
         */
        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("p1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(pluginService).delete("p1");
        }

        /**
         * 重新加载：委托 reload 并返回带加载状态的实体
         */
        @Test
        void reloadShouldDelegateAndReturnEntity() {
            PluginEntity reloaded = plugin("p1");
            reloaded.setLoaded(true);
            when(pluginService.reload("p1")).thenReturn(reloaded);

            R<PluginEntity> response = controller.reload("p1");

            assertThat(response.getData().getLoaded()).isTrue();
            verify(pluginService).reload("p1");
        }

        /**
         * 创建失败时业务异常向上传播
         */
        @Test
        void createShouldPropagateServiceException() {
            PluginEntity entity = plugin(null);
            when(pluginService.create(entity)).thenThrow(new ApiException("插件类名不能为空"));

            assertThatThrownBy(() -> controller.create(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("插件类名不能为空");
        }
    }

    @Nested
    @DisplayName("查询与扫描")
    class QueryOperations {

        /**
         * 详情：按路径 id 委托 getById
         */
        @Test
        void getShouldDelegateByPathId() {
            when(pluginService.getById("p1")).thenReturn(plugin("p1"));

            R<PluginEntity> response = controller.get("p1");

            assertThat(response.getData().getId()).isEqualTo("p1");
            verify(pluginService).getById("p1");
        }

        /**
         * 列表：Page 转 PageResult 并保留分页信息
         */
        @Test
        void listShouldConvertPageToPageResult() {
            Page<PluginEntity> page = new Page<>(3, 5);
            page.setRecords(List.of(plugin("p1")));
            page.setTotal(11);
            when(pluginService.pageQuery("kw", "parser", 3, 5)).thenReturn(page);

            R<PageResult<PluginEntity>> response = controller.list("kw", "parser", 3, 5);

            PageResult<PluginEntity> data = response.getData();
            assertThat(data.getList()).hasSize(1);
            assertThat(data.getTotal()).isEqualTo(11);
            assertThat(data.getPage()).isEqualTo(3);
            assertThat(data.getSize()).isEqualTo(5);
            verify(pluginService).pageQuery("kw", "parser", 3, 5);
        }

        /**
         * 列表：关键字/类型为 null、分页取默认值时原样透传
         */
        @Test
        void listShouldPassThroughNullsAndDefaults() {
            when(pluginService.pageQuery(null, null, 1, 10)).thenReturn(new Page<>(1, 10));

            R<PageResult<PluginEntity>> response = controller.list(null, null, 1, 10);

            assertThat(response.getData().getList()).isEmpty();
            verify(pluginService).pageQuery(null, null, 1, 10);
        }

        /**
         * 列表：服务层异常不被包装为成功响应
         */
        @Test
        void listShouldPropagateServiceException() {
            when(pluginService.pageQuery(any(), any(), any(), any()))
                    .thenThrow(new ApiException("查询失败"));

            assertThatThrownBy(() -> controller.list(null, null, 1, 10))
                    .isInstanceOf(ApiException.class);
        }

        /**
         * 扫描：返回容器中同步落库的插件列表
         */
        @Test
        void scanShouldReturnScannedPlugins() {
            when(pluginService.scanPlugins()).thenReturn(List.of(plugin("p1"), plugin("p2")));

            R<List<PluginEntity>> response = controller.scan();

            assertThat(response.getData()).extracting(PluginEntity::getId)
                    .containsExactly("p1", "p2");
            verify(pluginService).scanPlugins();
        }

        /**
         * 扫描：容器中没有插件时返回空列表而非 null
         */
        @Test
        void scanShouldReturnEmptyList() {
            when(pluginService.scanPlugins()).thenReturn(List.of());

            R<List<PluginEntity>> response = controller.scan();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
        }
    }
}
