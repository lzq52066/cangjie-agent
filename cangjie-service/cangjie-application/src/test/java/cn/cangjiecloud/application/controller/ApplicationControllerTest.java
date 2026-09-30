package cn.cangjiecloud.application.controller;

import cn.cangjiecloud.application.api.dto.ApplicationCreateDTO;
import cn.cangjiecloud.application.entity.ApplicationEntity;
import cn.cangjiecloud.application.service.IApplicationService;
import cn.cangjiecloud.application.service.IApplicationVersionService;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationController} 单元测试：不加载 Spring 上下文，
 * 直接实例化 Controller 并 mock 两个服务，验证请求委派与返回包装。
 */
class ApplicationControllerTest {

    private IApplicationService applicationService;
    private IApplicationVersionService applicationVersionService;
    private ApplicationController controller;

    @BeforeEach
    void setUp() {
        applicationService = mock(IApplicationService.class);
        applicationVersionService = mock(IApplicationVersionService.class);
        controller = new ApplicationController(applicationService, applicationVersionService);
    }

    private static ApplicationEntity application(String id) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(id);
        entity.setName("app");
        return entity;
    }

    private static ApplicationCreateDTO dto() {
        ApplicationCreateDTO dto = new ApplicationCreateDTO();
        dto.setName("新应用");
        dto.setType("chat");
        return dto;
    }

    @Nested
    class CrudEndpoints {

        @Test
        void createShouldDelegateToServiceAndWrapResult() {
            ApplicationEntity saved = application("a1");
            when(applicationService.create(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

            R<ApplicationEntity> response = controller.create(dto());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isSameAs(saved);
            verify(applicationService).create(org.mockito.ArgumentMatchers.any(ApplicationCreateDTO.class));
        }

        @Test
        void updateShouldDelegateWithPathId() {
            ApplicationEntity updated = application("a1");
            when(applicationService.update(org.mockito.ArgumentMatchers.eq("a1"),
                    org.mockito.ArgumentMatchers.any())).thenReturn(updated);

            R<ApplicationEntity> response = controller.update("a1", dto());

            assertThat(response.getData()).isSameAs(updated);
            verify(applicationService).update("a1", dto());
        }

        @Test
        void deleteShouldDelegateAndReturnOk() {
            R<Void> response = controller.delete("a1");

            assertThat(response.isSuccess()).isTrue();
            verify(applicationService).delete("a1");
        }

        @Test
        void getShouldDelegateToService() {
            when(applicationService.getById("a1")).thenReturn(application("a1"));

            R<ApplicationEntity> response = controller.get("a1");

            assertThat(response.getData().getId()).isEqualTo("a1");
            verify(applicationService).getById("a1");
        }

        @Test
        void getShouldAllowNullData() {
            when(applicationService.getById("missing")).thenReturn(null);

            assertThat(controller.get("missing").getData()).isNull();
        }
    }

    @Nested
    class ListAndPublish {

        @Test
        void listShouldWrapPageResultWithQueryParams() {
            Page<ApplicationEntity> page = new Page<>(2, 5);
            page.setRecords(List.of(application("a1"), application("a2")));
            page.setTotal(2);
            when(applicationService.pageQuery("kw", "agent", 2, 5)).thenReturn(page);

            R<PageResult<ApplicationEntity>> response = controller.list("kw", "agent", 2, 5);

            PageResult<ApplicationEntity> result = response.getData();
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(5);
            verify(applicationService).pageQuery("kw", "agent", 2, 5);
        }

        @Test
        void publishShouldPublishApplicationAndVersionWithGivenOperator() {
            ApplicationEntity published = application("a1");
            when(applicationService.publish("a1")).thenReturn(published);

            R<ApplicationEntity> response = controller.publish("a1", "alice");

            assertThat(response.getData()).isSameAs(published);
            verify(applicationService).publish("a1");
            verify(applicationVersionService).publish("a1", "alice");
        }

        @Test
        void publishShouldFallbackOperatorToSystemWhenMissing() {
            when(applicationService.publish("a1")).thenReturn(application("a1"));

            controller.publish("a1", null);

            verify(applicationVersionService).publish("a1", "system");
        }

        @Test
        void getByApikeyShouldDelegateToService() {
            when(applicationService.getByApikey("cj_key")).thenReturn(application("a1"));

            R<ApplicationEntity> response = controller.getByApikey("cj_key");

            assertThat(response.getData().getId()).isEqualTo("a1");
            verify(applicationService).getByApikey("cj_key");
        }
    }
}
