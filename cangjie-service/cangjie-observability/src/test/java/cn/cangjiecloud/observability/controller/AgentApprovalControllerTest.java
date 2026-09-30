package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.entity.AgentApprovalEntity;
import cn.cangjiecloud.observability.service.IAgentApprovalService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentApprovalController} 单元测试：验证分页条件透传、Page 包装与详情存在性分支。
 */
@ExtendWith(MockitoExtension.class)
class AgentApprovalControllerTest {

    @Mock
    private IAgentApprovalService approvalService;

    private AgentApprovalController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentApprovalController(approvalService);
    }

    private static AgentApprovalEntity approval(String id, String status) {
        AgentApprovalEntity entity = new AgentApprovalEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setRunId("run-1");
        entity.setToolName("shell_exec");
        return entity;
    }

    @Nested
    class PageQuery {

        @Test
        void shouldDelegateAllFiltersAndWrapPage() {
            // 正常场景：五个查询条件原样传给 Service，结果整体包进 R
            Page<AgentApprovalEntity> page = new Page<>(2, 20);
            page.setTotal(100L);
            page.setRecords(List.of(approval("a-1", "pending")));
            when(approvalService.pageQuery("pending", "app-1", "u-1", 2, 20)).thenReturn(page);

            R<IPage<AgentApprovalEntity>> response = controller.page("pending", "app-1", "u-1", 2, 20);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            assertThat(response.getData().getRecords())
                    .extracting(AgentApprovalEntity::getId)
                    .containsExactly("a-1");
            verify(approvalService).pageQuery("pending", "app-1", "u-1", 2, 20);
        }

        @Test
        void shouldPassNullConditionsAndPagingThroughAsIs() {
            // 边界场景：Controller 不做默认值兜底，null 条件由 Service 自己兜
            Page<AgentApprovalEntity> page = new Page<>(1, 10);
            when(approvalService.pageQuery(null, null, null, null, null)).thenReturn(page);

            R<IPage<AgentApprovalEntity>> response = controller.page(null, null, null, null, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            verify(approvalService).pageQuery(null, null, null, null, null);
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldReturnDataWhenApprovalExists() {
            // 正常场景：命中记录返回成功响应
            AgentApprovalEntity entity = approval("a-2", "approved");
            when(approvalService.getById("a-2")).thenReturn(entity);

            R<AgentApprovalEntity> response = controller.detail("a-2");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(entity);
            verify(approvalService).getById("a-2");
        }

        @Test
        void shouldReturnFailWhenApprovalMissing() {
            // 边界场景：记录不存在返回业务失败而不是空成功
            when(approvalService.getById("missing")).thenReturn(null);

            R<AgentApprovalEntity> response = controller.detail("missing");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMsg()).isEqualTo("审批单不存在");
            assertThat(response.getData()).isNull();
            verify(approvalService).getById("missing");
        }

        @Test
        void shouldPassPathIdVerbatimWithoutTrim() {
            // 边界场景：路径参数原样下传（不做 trim/大小写归一）
            AgentApprovalEntity entity = approval("  a-3  ", "pending");
            when(approvalService.getById("  a-3  ")).thenReturn(entity);

            R<AgentApprovalEntity> response = controller.detail("  a-3  ");

            assertThat(response.getData()).isSameAs(entity);
            verify(approvalService).getById("  a-3  ");
        }
    }
}
