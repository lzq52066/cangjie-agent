package cn.cangjiecloud.knowledge.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.dto.RetrievalQueryDTO;
import cn.cangjiecloud.knowledge.api.dto.RetrievalResultDTO;
import cn.cangjiecloud.knowledge.service.IRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RetrievalController} 单元测试。
 * <p>
 * 验证 search / hit-test 均委托 {@link IRetrievalService} 并原样包装结果，
 * 直接调用方法，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalControllerTest {

    @Mock
    private IRetrievalService retrievalService;

    private RetrievalController controller;

    @BeforeEach
    void setUp() {
        controller = new RetrievalController(retrievalService);
    }

    private static RetrievalQueryDTO query(String text) {
        RetrievalQueryDTO dto = new RetrievalQueryDTO();
        dto.setQuery(text);
        dto.setKnowledgeBaseId("kb1");
        return dto;
    }

    private static RetrievalResultDTO hit(String paragraphId, String matchedProblem) {
        return RetrievalResultDTO.builder()
                .paragraphId(paragraphId)
                .documentId("doc-" + paragraphId)
                .knowledgeBaseId("kb1")
                .content("内容-" + paragraphId)
                .finalScore(0.9)
                .matchedProblem(matchedProblem)
                .build();
    }

    @Nested
    class Search {

        @Test
        void shouldDelegateAndWrapResults() {
            // 正常场景：搜索透传查询对象并包装检索结果
            RetrievalQueryDTO dto = query("如何配置");
            List<RetrievalResultDTO> results = List.of(hit("p1", null));
            when(retrievalService.retrieve(dto)).thenReturn(results);

            R<List<RetrievalResultDTO>> r = controller.search(dto);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isSameAs(results);
            verify(retrievalService).retrieve(dto);
        }

        @Test
        void shouldReturnEmptyListWhenNoHits() {
            // 边界场景：无命中时返回空列表而非 null
            RetrievalQueryDTO dto = query("冷门问题");
            when(retrievalService.retrieve(dto)).thenReturn(List.of());

            R<List<RetrievalResultDTO>> r = controller.search(dto);

            assertThat(r.getData()).isEmpty();
        }

        @Test
        void shouldPropagateServiceException() {
            // 异常场景：Service 抛 ApiException 时向上传播，由全局异常处理
            RetrievalQueryDTO dto = query("无权限问题");
            when(retrievalService.retrieve(dto)).thenThrow(new ApiException("无权访问该知识库"));

            assertThatThrownBy(() -> controller.search(dto))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("无权访问");
        }
    }

    @Nested
    class HitTest {

        @Test
        void shouldDelegateToRetrieve() {
            // 正常场景：命中测试复用 retrieve 并返回调试明细
            RetrievalQueryDTO dto = query("调试检索");
            when(retrievalService.retrieve(dto)).thenReturn(List.of(hit("p1", null), hit("p2", null)));

            R<List<RetrievalResultDTO>> r = controller.hitTest(dto);

            assertThat(r.getData()).hasSize(2);
            verify(retrievalService).retrieve(dto);
        }

        @Test
        void shouldKeepMatchedProblemMark() {
            // 正常场景：问题路命中标记原样透出，便于调试
            RetrievalQueryDTO dto = query("常见问题");
            when(retrievalService.retrieve(dto)).thenReturn(List.of(hit("p1", "如何配置？")));

            R<List<RetrievalResultDTO>> r = controller.hitTest(dto);

            assertThat(r.getData()).hasSize(1);
            assertThat(r.getData().get(0).getMatchedProblem()).isEqualTo("如何配置？");
        }

        @Test
        void shouldReturnEmptyListWhenNoHits() {
            // 边界场景：命中测试无结果时返回空列表
            RetrievalQueryDTO dto = query("无结果");
            when(retrievalService.retrieve(dto)).thenReturn(List.of());

            R<List<RetrievalResultDTO>> r = controller.hitTest(dto);

            assertThat(r.getData()).isEmpty();
        }
    }
}
