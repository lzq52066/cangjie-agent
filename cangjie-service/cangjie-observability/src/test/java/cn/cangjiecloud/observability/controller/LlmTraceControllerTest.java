package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.service.ILlmTraceService;
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
 * {@link LlmTraceController} 单元测试：验证分页/详情/时间线接口的委托与不存在分支。
 */
@ExtendWith(MockitoExtension.class)
class LlmTraceControllerTest {

    @Mock
    private ILlmTraceService llmTraceService;

    private LlmTraceController controller;

    @BeforeEach
    void setUp() {
        controller = new LlmTraceController(llmTraceService);
    }

    private static LlmTraceEntity trace(String id, String modelName, String status) {
        LlmTraceEntity entity = new LlmTraceEntity();
        entity.setId(id);
        entity.setModelName(modelName);
        entity.setStatus(status);
        entity.setSessionId("session-1");
        return entity;
    }

    @Nested
    class PageQuery {

        @Test
        void shouldDelegateQueryDtoAndWrapPage() {
            // 正常场景：查询 DTO 原样传给 Service
            LlmTraceQueryDTO query = new LlmTraceQueryDTO();
            query.setModelName("gpt-4o");
            query.setStatus("success");
            query.setPageNum(3);
            query.setPageSize(30);

            Page<LlmTraceEntity> page = new Page<>(3, 30);
            page.setTotal(66L);
            page.setRecords(List.of(trace("t-1", "gpt-4o", "success")));
            when(llmTraceService.pageQuery(query)).thenReturn(page);

            R<IPage<LlmTraceEntity>> response = controller.page(query);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            assertThat(response.getData().getRecords())
                    .extracting(LlmTraceEntity::getModelName)
                    .containsExactly("gpt-4o");
            verify(llmTraceService).pageQuery(query);
        }

        @Test
        void shouldPassNullQueryThrough() {
            // 边界场景：DTO 为 null 时透传，由 Service 兜默认分页
            Page<LlmTraceEntity> page = new Page<>(1, 10);
            when(llmTraceService.pageQuery(null)).thenReturn(page);

            R<IPage<LlmTraceEntity>> response = controller.page(null);

            assertThat(response.getData()).isSameAs(page);
            verify(llmTraceService).pageQuery(null);
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldReturnDataWhenTraceExists() {
            // 正常场景：单条详情命中
            LlmTraceEntity entity = trace("t-2", "qwen-max", "fail");
            when(llmTraceService.getDetail("t-2")).thenReturn(entity);

            R<LlmTraceEntity> response = controller.detail("t-2");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(entity);
            verify(llmTraceService).getDetail("t-2");
        }

        @Test
        void shouldReturnFailWhenTraceMissing() {
            // 边界场景：记录不存在返回业务失败
            when(llmTraceService.getDetail("missing")).thenReturn(null);

            R<LlmTraceEntity> response = controller.detail("missing");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMsg()).isEqualTo("LLM 调用记录不存在");
            assertThat(response.getData()).isNull();
            verify(llmTraceService).getDetail("missing");
        }
    }

    @Nested
    class Timeline {

        @Test
        void shouldDelegateSessionIdAndWrapList() {
            // 正常场景：按会话查询时间线
            List<LlmTraceEntity> traces = List.of(
                    trace("t-3", "gpt-4o", "success"),
                    trace("t-4", "gpt-4o-mini", "fail"));
            when(llmTraceService.timeline("session-7")).thenReturn(traces);

            R<List<LlmTraceEntity>> response = controller.timeline("session-7");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(traces);
            verify(llmTraceService).timeline("session-7");
        }

        @Test
        void shouldReturnEmptyListAsSuccessWhenSessionHasNoTrace() {
            // 边界场景：会话无调用记录返回空列表而非失败
            when(llmTraceService.timeline("empty")).thenReturn(List.of());

            R<List<LlmTraceEntity>> response = controller.timeline("empty");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEmpty();
            verify(llmTraceService).timeline("empty");
        }
    }
}
