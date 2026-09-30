package cn.cangjiecloud.tool.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.tool.api.dto.ToolExecuteDTO;
import cn.cangjiecloud.tool.api.dto.ToolExecuteResultDTO;
import cn.cangjiecloud.tool.entity.ToolEntity;
import cn.cangjiecloud.tool.service.IToolService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolController} 单元测试：直接实例化控制器并 mock 服务层，不加载 Spring 上下文，
 * 验证各端点对 IToolService 的委托、分页包装与异常传播。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工具控制器 - ToolController")
class ToolControllerTest {

    @Mock
    private IToolService toolService;

    private ToolController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolController(toolService);
    }

    /**
     * 构造工具实体
     */
    private static ToolEntity tool(String id) {
        ToolEntity entity = new ToolEntity();
        entity.setId(id);
        entity.setName("工具-" + id);
        return entity;
    }

    @Nested
    @DisplayName("写操作")
    class WriteOperations {

        /**
         * 创建：委托服务并把返回实体包装进 R.data
         */
        @Test
        void createShouldDelegateAndWrapEntity() {
            ToolEntity entity = tool(null);
            entity.setName("新工具");
            ToolEntity created = tool("t1");
            when(toolService.create(entity)).thenReturn(created);

            R<ToolEntity> response = controller.create(entity);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getData()).isSameAs(created);
            verify(toolService).create(entity);
        }

        /**
         * 创建：服务层抛出的业务异常原样向上传播，控制器不做吞并
         */
        @Test
        void createShouldPropagateServiceException() {
            ToolEntity entity = tool(null);
            when(toolService.create(entity)).thenThrow(new ApiException("工具名称已存在"));

            assertThatThrownBy(() -> controller.create(entity))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("工具名称已存在");
        }

        /**
         * 更新：路径 id 与请求体一起透传给服务层
         */
        @Test
        void updateShouldDelegateWithPathId() {
            ToolEntity entity = tool(null);
            entity.setDescription("新描述");
            ToolEntity updated = tool("t1");
            when(toolService.update("t1", entity)).thenReturn(updated);

            R<ToolEntity> response = controller.update("t1", entity);

            assertThat(response.getData()).isSameAs(updated);
            verify(toolService).update("t1", entity);
        }

        /**
         * 删除：委托后返回成功码且 data 为空
         */
        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("t1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(toolService).delete("t1");
        }

        /**
         * 执行：从 DTO 中取出 input 转交服务，toolId 由路径提供
         */
        @Test
        void executeShouldExtractInputFromDto() {
            ToolExecuteDTO dto = new ToolExecuteDTO();
            dto.setToolId("ignored");
            dto.setInput(Map.of("q", "hello"));
            ToolExecuteResultDTO result = ToolExecuteResultDTO.builder()
                    .success(true).output("ok").executionTime(12L).build();
            when(toolService.executeTool("t1", Map.of("q", "hello"))).thenReturn(result);

            R<ToolExecuteResultDTO> response = controller.execute("t1", dto);

            assertThat(response.getData()).isSameAs(result);
            verify(toolService).executeTool("t1", Map.of("q", "hello"));
        }

        /**
         * 执行：DTO 未携带 input 时按 null 透传，由服务层决定默认行为
         */
        @Test
        void executeShouldPassNullInputWhenAbsent() {
            ToolExecuteDTO dto = new ToolExecuteDTO();
            ToolExecuteResultDTO result = ToolExecuteResultDTO.builder()
                    .success(false).error("缺少输入").build();
            when(toolService.executeTool(eq("t1"), any())).thenReturn(result);

            R<ToolExecuteResultDTO> response = controller.execute("t1", dto);

            assertThat(response.getData().getSuccess()).isFalse();
            verify(toolService).executeTool("t1", null);
        }
    }

    @Nested
    @DisplayName("查询操作")
    class QueryOperations {

        /**
         * 详情：按路径 id 委托 getById
         */
        @Test
        void getShouldDelegateByPathId() {
            when(toolService.getById("t1")).thenReturn(tool("t1"));

            R<ToolEntity> response = controller.get("t1");

            assertThat(response.getData().getId()).isEqualTo("t1");
            verify(toolService).getById("t1");
        }

        /**
         * 列表：MyBatis-Plus Page 被转换成 PageResult（list/total/page/size）
         */
        @Test
        void listShouldConvertPageToPageResult() {
            Page<ToolEntity> page = new Page<>(2, 10);
            page.setRecords(List.of(tool("t1"), tool("t2")));
            page.setTotal(25);
            when(toolService.pageQuery("kw", "PLUGIN", 2, 10)).thenReturn(page);

            R<PageResult<ToolEntity>> response = controller.list("kw", "PLUGIN", 2, 10);

            PageResult<ToolEntity> data = response.getData();
            assertThat(data.getList()).extracting(ToolEntity::getId).containsExactly("t1", "t2");
            assertThat(data.getTotal()).isEqualTo(25);
            assertThat(data.getPage()).isEqualTo(2);
            assertThat(data.getSize()).isEqualTo(10);
            verify(toolService).pageQuery("kw", "PLUGIN", 2, 10);
        }

        /**
         * 列表：关键字/类型为空、分页参数用默认值 1/10 时原样透传给服务层
         */
        @Test
        void listShouldPassThroughNullsAndDefaults() {
            Page<ToolEntity> empty = new Page<>(1, 10);
            when(toolService.pageQuery(null, null, 1, 10)).thenReturn(empty);

            R<PageResult<ToolEntity>> response = controller.list(null, null, 1, 10);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getList()).isEmpty();
            assertThat(response.getData().getTotal()).isZero();
            verify(toolService).pageQuery(null, null, 1, 10);
        }

        /**
         * 列表：服务层抛异常时不返回包装结果
         */
        @Test
        void listShouldPropagateServiceException() {
            when(toolService.pageQuery(any(), any(), any(), any()))
                    .thenThrow(new ApiException("查询失败"));

            assertThatThrownBy(() -> controller.list("kw", null, 1, 10))
                    .isInstanceOf(ApiException.class);
        }
    }
}
