package cn.cangjiecloud.knowledge.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.knowledge.api.dto.ProblemCreateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeProblemEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeProblemService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeProblemController} 单元测试。
 * <p>
 * 重点验证 associate 请求体解析（list / 非 list / 缺省）与各接口的委托包装，
 * 直接调用方法，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeProblemControllerTest {

    @Mock
    private IKnowledgeProblemService problemService;

    private KnowledgeProblemController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeProblemController(problemService);
    }

    private static KnowledgeProblemEntity problem(String id) {
        KnowledgeProblemEntity entity = new KnowledgeProblemEntity();
        entity.setId(id);
        entity.setKnowledgeBaseId("kb1");
        return entity;
    }

    @Nested
    class CrudDelegation {

        @Test
        void listShouldMapPagedProblems() {
            // 正常场景：问题分页映射为 PageResult
            Page<KnowledgeProblemEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(problem("q1")));
            page.setTotal(1L);
            when(problemService.pageQuery("kb1", 1, 10)).thenReturn(page);

            R<PageResult<KnowledgeProblemEntity>> r = controller.list("kb1", 1, 10);

            assertThat(r.getData().getList()).hasSize(1);
            assertThat(r.getData().getTotal()).isEqualTo(1L);
        }

        @Test
        void createShouldDelegateAndWrapEntity() {
            // 正常场景：创建问题返回实体
            ProblemCreateDTO dto = new ProblemCreateDTO();
            dto.setContent("如何配置？");
            KnowledgeProblemEntity created = problem("q1");
            when(problemService.create("kb1", dto)).thenReturn(created);

            R<KnowledgeProblemEntity> r = controller.create("kb1", dto);

            assertThat(r.getData()).isSameAs(created);
            verify(problemService).create("kb1", dto);
        }

        @Test
        void updateShouldDelegateAndWrapEntity() {
            // 正常场景：更新问题返回实体
            ProblemCreateDTO dto = new ProblemCreateDTO();
            dto.setContent("改名？");
            KnowledgeProblemEntity updated = problem("q1");
            when(problemService.update("q1", dto)).thenReturn(updated);

            R<KnowledgeProblemEntity> r = controller.update("q1", dto);

            assertThat(r.getData()).isSameAs(updated);
            verify(problemService).update("q1", dto);
        }

        @Test
        void deleteShouldReturnOk() {
            // 正常场景：删除返回成功响应
            R<Void> r = controller.delete("q1");

            assertThat(r.isSuccess()).isTrue();
            verify(problemService).delete("q1");
        }

        @Test
        void deleteShouldPropagateServiceException() {
            // 异常场景：Service 异常向上抛出
            doThrow(new RuntimeException("问题不存在"))
                    .when(problemService).delete("q9");
            assertThatThrownBy(() -> controller.delete("q9"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("不存在");
        }

        @Test
        void paragraphsShouldMapPagedIds() {
            // 正常场景：关联段落 ID 分页映射为 PageResult<String>
            Page<String> page = new Page<>(1, 10);
            page.setRecords(List.of("p1", "p2"));
            page.setTotal(2L);
            when(problemService.pageQueryParagraphIds("q1", 1, 10)).thenReturn(page);

            R<PageResult<String>> r = controller.paragraphs("q1", 1, 10);

            assertThat(r.getData().getList()).containsExactly("p1", "p2");
        }
    }

    @Nested
    class AssociateBodyParsing {

        @Test
        void shouldConvertListElementsToStrings() {
            // 正常场景：paragraphIds 为 list 时逐项转字符串透传
            R<Void> r = controller.associate("q1", Map.of("paragraphIds", List.of("p1", "p2")));

            assertThat(r.isSuccess()).isTrue();
            verify(problemService).associate("q1", List.of("p1", "p2"));
        }

        @Test
        void shouldHandleMixedElementTypes() {
            // 边界场景：list 中混入非字符串元素（数字）时按 String.valueOf 转换
            R<Void> r = controller.associate("q1", Map.of("paragraphIds", List.of(1, 2)));

            assertThat(r.isSuccess()).isTrue();
            verify(problemService).associate("q1", List.of("1", "2"));
        }

        @Test
        void shouldFallbackToEmptyListWhenValueNotList() {
            // 异常场景：paragraphIds 非 list（如字符串）时退化为空列表
            R<Void> r = controller.associate("q1", Map.of("paragraphIds", "p1"));

            assertThat(r.isSuccess()).isTrue();
            verify(problemService).associate("q1", List.of());
        }

        @Test
        void shouldFallbackToEmptyListWhenKeyMissing() {
            // 边界场景：请求体缺少 paragraphIds 键时退化为空列表
            R<Void> r = controller.associate("q1", Map.of());

            assertThat(r.isSuccess()).isTrue();
            verify(problemService).associate("q1", List.of());
        }
    }
}
