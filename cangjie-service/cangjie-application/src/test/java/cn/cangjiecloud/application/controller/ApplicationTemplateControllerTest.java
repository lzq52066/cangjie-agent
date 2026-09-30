package cn.cangjiecloud.application.controller;

import cn.cangjiecloud.application.api.dto.ApplicationTemplateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.entity.ApplicationTemplateEntity;
import cn.cangjiecloud.application.service.IApplicationTemplateService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationTemplateController} 单元测试：直接实例化 Controller 并 mock 模板服务，
 * 验证保存模板、分页、从模板建应用、导入导出与删除等请求的委派逻辑。
 */
class ApplicationTemplateControllerTest {

    private IApplicationTemplateService templateService;
    private ApplicationTemplateController controller;

    @BeforeEach
    void setUp() {
        templateService = mock(IApplicationTemplateService.class);
        controller = new ApplicationTemplateController(templateService);
    }

    private static ApplicationTemplateEntity template(String id) {
        ApplicationTemplateEntity entity = new ApplicationTemplateEntity();
        entity.setId(id);
        entity.setName("模板");
        return entity;
    }

    @Nested
    class TemplateEndpoints {

        @Test
        void saveFromApplicationShouldPassAllDtoFields() {
            ApplicationTemplateDTO dto = new ApplicationTemplateDTO();
            dto.setApplicationId("app-1");
            dto.setName("我的模板");
            dto.setDescription("描述");
            dto.setCategory("客服");
            when(templateService.saveFromApplication("app-1", "我的模板", "描述", "客服"))
                    .thenReturn(template("t1"));

            R<ApplicationTemplateEntity> response = controller.saveFromApplication(dto);

            assertThat(response.getData().getId()).isEqualTo("t1");
            verify(templateService).saveFromApplication("app-1", "我的模板", "描述", "客服");
        }

        @Test
        void listShouldWrapPagedTemplates() {
            Page<ApplicationTemplateEntity> page = new Page<>(1, 10);
            page.setRecords(List.of(template("t1")));
            page.setTotal(1);
            when(templateService.pageTemplates("客服", 1, 10)).thenReturn(page);

            R<PageResult<ApplicationTemplateEntity>> response = controller.list("客服", 1, 10);

            assertThat(response.getData().getList()).hasSize(1);
            assertThat(response.getData().getTotal()).isEqualTo(1);
            verify(templateService).pageTemplates("客服", 1, 10);
        }

        @Test
        void createFromTemplateShouldExtractAppNameFromBody() {
            ApplicationEntity app = new ApplicationEntity();
            app.setId("app-new");
            when(templateService.createFromTemplate("t1", "新应用")).thenReturn(app);

            R<ApplicationEntity> response =
                    controller.createFromTemplate("t1", Map.of("name", "新应用"));

            assertThat(response.getData().getId()).isEqualTo("app-new");
            verify(templateService).createFromTemplate("t1", "新应用");
        }

        @Test
        void createFromTemplateShouldPassNullNameWhenBodyMissing() {
            when(templateService.createFromTemplate("t1", null))
                    .thenReturn(new ApplicationEntity());

            controller.createFromTemplate("t1", null);

            verify(templateService).createFromTemplate("t1", null);
        }

        @Test
        void importBundleShouldDelegateRawJsonAsNonBuiltin() {
            when(templateService.importBundle("{\"name\":\"x\"}", false))
                    .thenReturn(template("t2"));

            R<ApplicationTemplateEntity> response = controller.importBundle("{\"name\":\"x\"}");

            assertThat(response.getData().getId()).isEqualTo("t2");
            verify(templateService).importBundle("{\"name\":\"x\"}", false);
        }

        @Test
        void exportBundleShouldReturnJsonString() {
            when(templateService.exportBundle("t1")).thenReturn("{\"templateKey\":\"k\"}");

            R<String> response = controller.exportBundle("t1");

            assertThat(response.getData()).isEqualTo("{\"templateKey\":\"k\"}");
            verify(templateService).exportBundle("t1");
        }

        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("t1");

            assertThat(response.isSuccess()).isTrue();
            verify(templateService).deleteTemplate("t1");
        }
    }
}
