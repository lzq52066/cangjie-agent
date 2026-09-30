package cn.cangjiecloud.observability.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.observability.dto.TraceLinkDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import cn.cangjiecloud.observability.service.ITraceLinkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceLinkController} 单元测试：验证链路分页透传与 detail 的 linkId 查询参数分支。
 */
@ExtendWith(MockitoExtension.class)
class TraceLinkControllerTest {

    @Mock
    private ITraceLinkService traceLinkService;

    private TraceLinkController controller;

    @BeforeEach
    void setUp() {
        controller = new TraceLinkController(traceLinkService);
    }

    private static TraceLinkVO link(String traceId, String status) {
        TraceLinkVO vo = new TraceLinkVO();
        vo.setTraceId(traceId);
        vo.setStatus(status);
        vo.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return vo;
    }

    @Nested
    class PageQuery {

        @Test
        void shouldDelegateQueryDtoAndWrapPage() {
            // 正常场景：查询 DTO 原样传给 Service
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            query.setStatus("fail");
            query.setPageNum(2);
            query.setPageSize(20);

            Page<TraceLinkVO> page = new Page<>(2, 20);
            page.setTotal(5L);
            page.setRecords(List.of(link("trace-1", "fail")));
            when(traceLinkService.pageQuery(query)).thenReturn(page);

            R<IPage<TraceLinkVO>> response = controller.page(query);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(page);
            assertThat(response.getData().getRecords())
                    .extracting(TraceLinkVO::getTraceId)
                    .containsExactly("trace-1");
            verify(traceLinkService).pageQuery(query);
        }

        @Test
        void shouldPassNullQueryThrough() {
            // 边界场景：DTO 为 null 时透传，由 Service 兜默认回溯窗口与分页
            Page<TraceLinkVO> page = new Page<>(1, 20);
            when(traceLinkService.pageQuery(null)).thenReturn(page);

            R<IPage<TraceLinkVO>> response = controller.page(null);

            assertThat(response.getData()).isSameAs(page);
            verify(traceLinkService).pageQuery(null);
        }

        @Test
        void shouldReturnEmptyPageAsSuccessWhenNoLinkMatched() {
            // 边界场景：无命中链路返回空页而非失败
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            query.setTraceId("none");
            Page<TraceLinkVO> page = new Page<>(1, 20);
            when(traceLinkService.pageQuery(query)).thenReturn(page);

            R<IPage<TraceLinkVO>> response = controller.page(query);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getRecords()).isEmpty();
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldPassIsolatedLinkKeyWithHashPrefixVerbatim() {
            // 关键场景：孤立链路键带 # 占位前缀，必须原样经查询参数传给 Service
            TraceLinkDetailVO detail = new TraceLinkDetailVO();
            detail.setSummary(link("trace-2", "success"));
            when(traceLinkService.detail("#span#123")).thenReturn(detail);

            R<TraceLinkDetailVO> response = controller.detail("#span#123");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(detail);
            verify(traceLinkService).detail("#span#123");
        }

        @Test
        void shouldReturnFailWhenLinkExpiredOrMissing() {
            // 边界场景：链路不存在或超出保留期返回业务失败
            when(traceLinkService.detail("trace-gone")).thenReturn(null);

            R<TraceLinkDetailVO> response = controller.detail("trace-gone");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMsg()).isEqualTo("链路不存在或已超出保留期");
            assertThat(response.getData()).isNull();
            verify(traceLinkService).detail("trace-gone");
        }
    }
}
