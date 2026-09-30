package cn.cangjiecloud.application.controller;

import cn.cangjiecloud.application.api.dto.ApplicationRollbackDTO;
import cn.cangjiecloud.application.api.dto.ApplicationVersionDTO;
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
 * {@link ApplicationVersionController} 单元测试：直接实例化 Controller 并 mock 版本服务，
 * 验证版本分页、详情、回滚与删除请求的委派逻辑及提示语。
 */
class ApplicationVersionControllerTest {

    private IApplicationVersionService versionService;
    private ApplicationVersionController controller;

    @BeforeEach
    void setUp() {
        versionService = mock(IApplicationVersionService.class);
        controller = new ApplicationVersionController(versionService);
    }

    private static ApplicationVersionDTO version(String id, int version) {
        return ApplicationVersionDTO.builder().id(id).version(version)
                .applicationId("app-1").build();
    }

    @Nested
    class VersionEndpoints {

        @Test
        void pageByApplicationShouldWrapPagedDtos() {
            Page<ApplicationVersionDTO> page = new Page<>(1, 10);
            page.setRecords(List.of(version("v1", 1), version("v2", 2)));
            page.setTotal(2);
            when(versionService.pageByApplication("app-1", 1, 10)).thenReturn(page);

            R<PageResult<ApplicationVersionDTO>> response =
                    controller.pageByApplication("app-1", 1, 10);

            PageResult<ApplicationVersionDTO> result = response.getData();
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            verify(versionService).pageByApplication("app-1", 1, 10);
        }

        @Test
        void getShouldReturnVersionDto() {
            when(versionService.getVersion("v1")).thenReturn(version("v1", 1));

            R<ApplicationVersionDTO> response = controller.get("v1");

            assertThat(response.getData().getVersion()).isEqualTo(1);
            verify(versionService).getVersion("v1");
        }

        @Test
        void getShouldAllowNullData() {
            when(versionService.getVersion("missing")).thenReturn(null);

            assertThat(controller.get("missing").getData()).isNull();
        }

        @Test
        void rollbackShouldDelegateIdsAndReturnMessage() {
            ApplicationRollbackDTO dto = new ApplicationRollbackDTO();
            dto.setVersionId("v1");

            R<Void> response = controller.rollback("app-1", dto);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMsg()).isEqualTo("应用已回滚到指定版本");
            verify(versionService).rollback("app-1", "v1");
        }

        @Test
        void deleteShouldDelegateAndReturnMessage() {
            R<Void> response = controller.delete("v1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMsg()).isEqualTo("版本已删除");
            verify(versionService).delete("v1");
        }
    }
}
