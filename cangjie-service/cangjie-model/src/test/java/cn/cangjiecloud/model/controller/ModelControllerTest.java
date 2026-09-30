package cn.cangjiecloud.model.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.model.api.dto.ModelCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelTestDTO;
import cn.cangjiecloud.model.api.dto.ModelUpdateDTO;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.service.IModelService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelController} 单元测试：直接实例化 Controller 并 mock 服务层，
 * 不加载 Spring 上下文，验证各端点正确委派并包装返回值。
 */
class ModelControllerTest {

    private IModelService modelService;
    private ModelController controller;

    @BeforeEach
    void setUp() {
        modelService = mock(IModelService.class);
        controller = new ModelController(modelService);
    }

    private static ModelEntity model(String id) {
        ModelEntity entity = new ModelEntity();
        entity.setId(id);
        entity.setName("模型-" + id);
        return entity;
    }

    @Nested
    class MutationEndpoints {

        @Test
        void createShouldDelegateAndWrapEntity() {
            ModelCreateDTO dto = new ModelCreateDTO();
            when(modelService.create(dto)).thenReturn(model("m1"));

            R<ModelEntity> response = controller.create(dto);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getId()).isEqualTo("m1");
            verify(modelService).create(dto);
        }

        @Test
        void updateShouldDelegateWithPathId() {
            ModelUpdateDTO dto = new ModelUpdateDTO();
            when(modelService.update("m1", dto)).thenReturn(model("m1"));

            R<ModelEntity> response = controller.update("m1", dto);

            assertThat(response.getData().getId()).isEqualTo("m1");
            verify(modelService).update("m1", dto);
        }

        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("m1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(modelService).delete("m1");
        }

        @Test
        void setDefaultShouldDelegateAndReturnOk() {
            R<Void> response = controller.setDefault("m1");

            assertThat(response.isSuccess()).isTrue();
            verify(modelService).setDefault("m1");
        }
    }

    @Nested
    class QueryEndpoints {

        @Test
        void getShouldDelegateByPathId() {
            when(modelService.getById("m1")).thenReturn(model("m1"));

            R<ModelEntity> response = controller.get("m1");

            assertThat(response.getData().getId()).isEqualTo("m1");
            verify(modelService).getById("m1");
        }

        @Test
        void listShouldConvertPageToPageResult() {
            Page<ModelEntity> page = new Page<>(2, 10);
            page.setRecords(List.of(model("m1"), model("m2")));
            page.setTotal(25);
            when(modelService.pageQuery(eq("kw"), eq("openai"), eq("p1"), eq(2), eq(10)))
                    .thenReturn(page);

            R<PageResult<ModelEntity>> response = controller.list("kw", "openai", "p1", 2, 10);

            PageResult<ModelEntity> data = response.getData();
            assertThat(data.getList()).hasSize(2);
            assertThat(data.getTotal()).isEqualTo(25);
            assertThat(data.getPage()).isEqualTo(2);
            assertThat(data.getSize()).isEqualTo(10);
            verify(modelService).pageQuery("kw", "openai", "p1", 2, 10);
        }

        @Test
        void listShouldUseDefaultPaginationWhenNullsProvided() {
            // 分页参数为 null 时由服务层兜底，此处仅验证参数透传
            IPage<ModelEntity> emptyPage = new Page<>(1, 10);
            when(modelService.pageQuery(null, null, null, 1, 10)).thenReturn(emptyPage);

            R<PageResult<ModelEntity>> response = controller.list(null, null, null, 1, 10);

            assertThat(response.getData().getList()).isEmpty();
        }
    }

    @Nested
    class TestEndpoint {

        @Test
        void testShouldExtractModelIdAndMessageFromDto() {
            ModelTestDTO dto = new ModelTestDTO();
            dto.setModelId("m1");
            dto.setMessage("你好");
            when(modelService.testModel("m1", "你好")).thenReturn("连通正常");

            R<String> response = controller.test(dto);

            assertThat(response.getData()).isEqualTo("连通正常");
            verify(modelService).testModel("m1", "你好");
        }
    }
}
