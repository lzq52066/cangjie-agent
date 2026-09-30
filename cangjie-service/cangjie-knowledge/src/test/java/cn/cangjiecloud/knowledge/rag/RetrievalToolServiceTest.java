package cn.cangjiecloud.knowledge.rag;

import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.core.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RetrievalToolService} 单元测试。
 * <p>
 * 覆盖：ToolSpecification 构建、入参校验错误文案、空结果提示、结果格式化与检索异常降级。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalToolServiceTest {

    @Mock
    private HybridRetriever hybridRetriever;

    private RetrievalToolService service;

    @BeforeEach
    void setUp() {
        service = new RetrievalToolService(hybridRetriever);
    }

    @Test
    void buildSpec应包含完整工具定义() {
        ToolSpecification spec = service.buildSpec("tool-1");

        assertThat(spec.getToolId()).isEqualTo("tool-1");
        assertThat(spec.getName()).isEqualTo("search_knowledge_base");
        assertThat(spec.getToolType()).isEqualTo("retrieval");
        assertThat(spec.getDescription()).isNotBlank();

        Map<String, Object> parameters = spec.getParameters();
        assertThat(parameters).containsEntry("type", "object");
        assertThat(parameters.get("required")).isEqualTo(List.of("query"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertThat(properties).containsOnlyKeys("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> queryProperty = (Map<String, Object>) properties.get("query");
        assertThat(queryProperty).containsEntry("type", "string");
        assertThat(String.valueOf(queryProperty.get("description"))).isNotBlank();
    }

    @Nested
    class 入参校验 {

        @Test
        void query为空时应返回错误提示且不触发检索() {
            assertThat(service.execute(null, List.of("kb1"))).isEqualTo("检索失败：查询词不能为空");
            assertThat(service.execute("", List.of("kb1"))).isEqualTo("检索失败：查询词不能为空");
            verifyNoInteractions(hybridRetriever);
        }

        @Test
        void 知识库为空时应返回错误提示且不触发检索() {
            assertThat(service.execute("查询", null)).isEqualTo("检索失败：未指定知识库");
            assertThat(service.execute("查询", List.of())).isEqualTo("检索失败：未指定知识库");
            verifyNoInteractions(hybridRetriever);
        }
    }

    @Nested
    class 检索执行 {

        @Test
        void 无结果时应返回未检索到提示() {
            when(hybridRetriever.retrieve(any(), anyList(), anyInt())).thenReturn(List.of());

            assertThat(service.execute("查询", List.of("kb1"))).isEqualTo("未检索到相关内容。");
        }

        @Test
        void 结果应格式化为编号文本并默认取TopK5() {
            List<RetrievalResult> results = List.of(
                    RetrievalResult.builder().paragraphId("p1").documentId("doc1")
                            .content("内容一").finalScore(0.93).build(),
                    RetrievalResult.builder().paragraphId("p2").documentId("doc2")
                            .content("内容二").finalScore(0.5).build());
            when(hybridRetriever.retrieve(eq("问题"), eq(List.of("kb1", "kb2")), eq(5))).thenReturn(results);

            String output = service.execute("问题", List.of("kb1", "kb2"));

            assertThat(output)
                    .startsWith("检索结果（共 2 条）：")
                    .contains("[1] 内容一")
                    .contains("（来源: doc1, 分数: 0.93）")
                    .contains("[2] 内容二")
                    .contains("（来源: doc2, 分数: 0.50）");
            verify(hybridRetriever).retrieve("问题", List.of("kb1", "kb2"), 5);
        }

        @Test
        void 检索异常时应返回失败信息() {
            when(hybridRetriever.retrieve(any(), anyList(), anyInt()))
                    .thenThrow(new RuntimeException("检索服务超时"));

            assertThat(service.execute("查询", List.of("kb1"))).isEqualTo("检索失败: 检索服务超时");
        }
    }
}
