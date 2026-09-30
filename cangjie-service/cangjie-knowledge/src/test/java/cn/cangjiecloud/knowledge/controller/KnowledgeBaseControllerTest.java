package cn.cangjiecloud.knowledge.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * {@link KnowledgeBaseController} 单元测试。
 * <p>
 * 直接调用控制器方法，验证对 Service 的委托与 {@link R}/{@link PageResult} 包装结果，
 * 不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    @Mock
    private IKnowledgeBaseService knowledgeBaseService;

    private KnowledgeBaseController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeBaseController(knowledgeBaseService);
    }

    private static KnowledgeBaseEntity base(String id) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setName("知识库-" + id);
        return entity;
    }

    @Nested
    class WriteOperations {

        @Test
        void createShouldDelegateAndWrapEntity() {
            // 正常场景：创建成功返回 R.data(entity)
            KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
            dto.setName("新知识库");
            KnowledgeBaseEntity created = base("kb1");
            when(knowledgeBaseService.create(dto)).thenReturn(created);

            R<KnowledgeBaseEntity> r = controller.create(dto);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isSameAs(created);
        }

        @Test
        void createShouldPropagateServiceException() {
            // 异常场景：Service 校验失败时异常向上抛出
            KnowledgeBaseCreateDTO dto = new KnowledgeBaseCreateDTO();
            dto.setName("重复名称");
            when(knowledgeBaseService.create(dto)).thenThrow(new ApiException("知识库名称已存在"));

            assertThatThrownBy(() -> controller.create(dto))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        void updateShouldPassIdAndDto() {
            // 正常场景：更新透传路径 ID 与 DTO
            KnowledgeBaseUpdateDTO dto = new KnowledgeBaseUpdateDTO();
            dto.setName("改名后");
            KnowledgeBaseEntity updated = base("kb1");
            when(knowledgeBaseService.update("kb1", dto)).thenReturn(updated);

            R<KnowledgeBaseEntity> r = controller.update("kb1", dto);

            assertThat(r.getData()).isSameAs(updated);
            verify(knowledgeBaseService).update("kb1", dto);
        }

        @Test
        void deleteShouldReturnOk() {
            // 正常场景：删除返回空数据的成功响应
            R<Void> r = controller.delete("kb1");

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isNull();
            verify(knowledgeBaseService).delete("kb1");
        }
    }

    @Nested
    class ReadOperations {

        @Test
        void getShouldReturnEntity() {
            // 正常场景：按 ID 查询返回实体
            KnowledgeBaseEntity entity = base("kb1");
            when(knowledgeBaseService.getById("kb1")).thenReturn(entity);

            R<KnowledgeBaseEntity> r = controller.get("kb1");

            assertThat(r.getData()).isSameAs(entity);
        }

        @Test
        void getShouldReturnNullDataWhenMissing() {
            // 边界场景：实体不存在时仍为成功响应、data 为 null
            when(knowledgeBaseService.getById("kb1")).thenReturn(null);

            R<KnowledgeBaseEntity> r = controller.get("kb1");

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getData()).isNull();
        }

        @Test
        void listShouldMapPageToPageResult() {
            // 正常场景：分页对象映射为 PageResult（list/total/page/size）
            Page<KnowledgeBaseEntity> page = new Page<>(2, 5);
            page.setRecords(List.of(base("kb1"), base("kb2")));
            page.setTotal(7L);
            when(knowledgeBaseService.pageQuery("keyword", 2, 5)).thenReturn(page);

            R<PageResult<KnowledgeBaseEntity>> r = controller.list("keyword", 2, 5);

            assertThat(r.getData().getList()).hasSize(2);
            assertThat(r.getData().getTotal()).isEqualTo(7L);
            assertThat(r.getData().getPage()).isEqualTo(2L);
            assertThat(r.getData().getSize()).isEqualTo(5L);
            verify(knowledgeBaseService).pageQuery("keyword", 2, 5);
        }

        @Test
        void listShouldPassNullKeywordThrough() {
            // 边界场景：keyword 缺省为 null 时原样透传
            Page<KnowledgeBaseEntity> page = new Page<>(1, 10);
            page.setRecords(List.of());
            when(knowledgeBaseService.pageQuery(null, 1, 10)).thenReturn(page);

            R<PageResult<KnowledgeBaseEntity>> r = controller.list(null, 1, 10);

            assertThat(r.getData().getList()).isEmpty();
            assertThat(r.getData().getTotal()).isZero();
        }
    }
}
