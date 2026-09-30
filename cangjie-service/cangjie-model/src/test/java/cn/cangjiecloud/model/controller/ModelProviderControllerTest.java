package cn.cangjiecloud.model.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.model.api.dto.ModelProviderCreateDTO;
import cn.cangjiecloud.model.api.dto.ModelProviderUpdateDTO;
import cn.cangjiecloud.model.entity.ModelProviderEntity;
import cn.cangjiecloud.model.service.IModelProviderService;
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
 * {@link ModelProviderController} 单元测试：直接实例化 Controller 并 mock 服务层，
 * 不加载 Spring 上下文，验证各端点正确委派并包装返回值。
 */
class ModelProviderControllerTest {

    private IModelProviderService providerService;
    private ModelProviderController controller;

    @BeforeEach
    void setUp() {
        providerService = mock(IModelProviderService.class);
        controller = new ModelProviderController(providerService);
    }

    private static ModelProviderEntity provider(String id) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId(id);
        entity.setName("厂商-" + id);
        return entity;
    }

    @Nested
    class MutationEndpoints {

        @Test
        void createShouldDelegateAndWrapEntity() {
            ModelProviderCreateDTO dto = new ModelProviderCreateDTO();
            when(providerService.create(dto)).thenReturn(provider("p1"));

            R<ModelProviderEntity> response = controller.create(dto);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getId()).isEqualTo("p1");
            verify(providerService).create(dto);
        }

        @Test
        void updateShouldDelegateWithPathId() {
            ModelProviderUpdateDTO dto = new ModelProviderUpdateDTO();
            when(providerService.update("p1", dto)).thenReturn(provider("p1"));

            R<ModelProviderEntity> response = controller.update("p1", dto);

            assertThat(response.getData().getId()).isEqualTo("p1");
            verify(providerService).update("p1", dto);
        }

        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("p1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            verify(providerService).delete("p1");
        }
    }

    @Nested
    class QueryEndpoints {

        @Test
        void listShouldConvertPageToPageResult() {
            Page<ModelProviderEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(provider("p1"), provider("p2")));
            page.setTotal(2);
            when(providerService.pageQuery(eq("open"), eq("active"), eq(1), eq(10)))
                    .thenReturn(page);

            R<PageResult<ModelProviderEntity>> response = controller.list("open", "active", 1, 10);

            PageResult<ModelProviderEntity> data = response.getData();
            assertThat(data.getList()).hasSize(2);
            assertThat(data.getTotal()).isEqualTo(2);
            assertThat(data.getPage()).isEqualTo(1);
            assertThat(data.getSize()).isEqualTo(10);
            verify(providerService).pageQuery("open", "active", 1, 10);
        }
    }
}
