package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.AgentRunQueryDTO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.AgentRunStepEntity;
import cn.cangjiecloud.observability.service.IAgentRunService;
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
 * {@link AgentRunController} 单元测试：验证分页/详情/步骤/会话时间线四个接口的委托与空值分支。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunControllerTest {

    @Mock
    private IAgentRunService agentRunService;

    private AgentRunController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentRunController(agentRunService);
    }

    private static AgentRunEntity run(String id, String status) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setSessionId("session-1");
        entity.setHarnessType("chat");
        return entity;
    }

    @Nested
    class PageQuery {

        @Test
        void shouldDelegateQueryDtoAndWrapPage() {
            // 正常场景：查询 DTO 原样传给 Service
            AgentRunQueryDTO query = new AgentRunQueryDTO();
            query.setStatus("running");
            query.setPageNum(2);
            query.setPageSize(50);

            Page<AgentRunEntity> page = new Page<>(2, 50);
            page.setTotal(3L);
            page.setRecords(List.of(run("run-1", "running")));
            when(agentRunService.pageQuery(query)).thenReturn(page);

            R<IPage<AgentRunEntity>> response = controller.page(query);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            verify(agentRunService).pageQuery(query);
        }

        @Test
        void shouldPassNullQueryThrough() {
            // 边界场景：DTO 为 null 时直接透传（由 Service 内部兜默认分页）
            Page<AgentRunEntity> page = new Page<>(1, 10);
            when(agentRunService.pageQuery(null)).thenReturn(page);

            R<IPage<AgentRunEntity>> response = controller.page(null);

            assertThat(response.getData()).isSameAs(page);
            verify(agentRunService).pageQuery(null);
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldReturnDetailWhenRunExists() {
            // 正常场景：详情命中
            AgentRunDetailVO detail = new AgentRunDetailVO();
            detail.setRun(run("run-2", "completed"));
            when(agentRunService.detail("run-2")).thenReturn(detail);

            R<AgentRunDetailVO> response = controller.detail("run-2");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(detail);
            verify(agentRunService).detail("run-2");
        }

        @Test
        void shouldReturnFailWhenRunMissing() {
            // 边界场景：run 不存在返回业务失败
            when(agentRunService.detail("missing")).thenReturn(null);

            R<AgentRunDetailVO> response = controller.detail("missing");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMsg()).isEqualTo("Agent run 不存在");
            assertThat(response.getData()).isNull();
            verify(agentRunService).detail("missing");
        }
    }

    @Nested
    class Steps {

        @Test
        void shouldDelegateRunIdAndWrapStepList() {
            // 正常场景：路径参数 runId 原样传给步骤查询
            AgentRunStepEntity step = new AgentRunStepEntity();
            step.setRunId("run-3");
            step.setStepNo(1);
            List<AgentRunStepEntity> steps = List.of(step);
            when(agentRunService.listSteps("run-3")).thenReturn(steps);

            R<List<AgentRunStepEntity>> response = controller.steps("run-3");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(steps);
            verify(agentRunService).listSteps("run-3");
        }
    }

    @Nested
    class Timeline {

        @Test
        void shouldDelegateSessionIdAndWrapRunList() {
            // 正常场景：按会话查询时间线
            List<AgentRunEntity> runs = List.of(run("run-4", "completed"), run("run-5", "failed"));
            when(agentRunService.listBySession("session-9")).thenReturn(runs);

            R<List<AgentRunEntity>> response = controller.timeline("session-9");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(runs);
            verify(agentRunService).listBySession("session-9");
        }

        @Test
        void shouldReturnEmptyListAsSuccessWhenSessionHasNoRun() {
            // 边界场景：会话无留痕返回空列表而非失败
            when(agentRunService.listBySession("empty-session")).thenReturn(List.of());

            R<List<AgentRunEntity>> response = controller.timeline("empty-session");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
            verify(agentRunService).listBySession("empty-session");
        }
    }
}
