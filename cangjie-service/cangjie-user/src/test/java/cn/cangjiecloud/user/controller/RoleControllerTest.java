package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.service.IRoleService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoleController} 单元测试：验证每个接口对 {@link IRoleService} 的委托、
 * 分页参数组装（RoleQueryDTO）以及 Page 到 PageResult 的转换。
 */
class RoleControllerTest {

    private IRoleService roleService;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        roleService = mock(IRoleService.class);
        controller = new RoleController(roleService);
    }

    private static RoleEntity role(String id, String code) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setName("角色-" + id);
        role.setCode(code);
        role.setStatus("active");
        return role;
    }

    @Test
    void createShouldDelegateAndWrapResult() {
        // 正常场景：创建角色
        RoleDTO dto = new RoleDTO();
        dto.setCode("ops");
        RoleEntity saved = role("r1", "ops");
        when(roleService.create(dto)).thenReturn(saved);

        R<RoleEntity> response = controller.create(dto);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(saved);
        verify(roleService).create(dto);
    }

    @Test
    void updateShouldPassIdAndDto() {
        // 正常场景：更新角色，路径 id 必须原样传给 Service
        RoleDTO dto = new RoleDTO();
        dto.setName("新名称");
        RoleEntity updated = role("r2", "dev");
        when(roleService.update("r2", dto)).thenReturn(updated);

        R<RoleEntity> response = controller.update("r2", dto);

        assertThat(response.getData()).isSameAs(updated);
        verify(roleService).update("r2", dto);
    }

    @Test
    void deleteShouldDelegateAndReturnOk() {
        // 正常场景：删除角色返回无 data 的成功响应
        R<Void> response = controller.delete("r3");

        verify(roleService).delete("r3");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteShouldPropagateServiceException() {
        // 异常场景：角色不存在时 Service 抛异常，Controller 不吞异常
        doThrow(new IllegalStateException("角色不存在")).when(roleService).delete("missing");

        assertThatThrownBy(() -> controller.delete("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("角色不存在");
    }

    @Test
    void getShouldReturnRoleById() {
        // 正常场景：按 ID 查询
        RoleEntity role = role("r4", "admin");
        when(roleService.getById("r4")).thenReturn(role);

        assertThat(controller.get("r4").getData()).isSameAs(role);
    }

    @Test
    void getShouldReturnNullDataWhenRoleNotExists() {
        // 边界场景：查询不到时 data 为空
        when(roleService.getById("none")).thenReturn(null);

        R<RoleEntity> response = controller.get("none");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void pageShouldAssembleQueryDtoAndConvertToPageResult() {
        // 正常场景：分页参数组装成 RoleQueryDTO，并把 IPage 转成 PageResult
        Page<RoleEntity> page = new Page<>(2, 20);
        page.setRecords(List.of(role("r1", "ops")));
        page.setTotal(35L);
        when(roleService.pageQuery(any(RoleQueryDTO.class))).thenReturn(page);

        R<PageResult<RoleEntity>> response = controller.page("adm", "active", 2, 20);

        ArgumentCaptor<RoleQueryDTO> captor = ArgumentCaptor.forClass(RoleQueryDTO.class);
        verify(roleService).pageQuery(captor.capture());
        RoleQueryDTO query = captor.getValue();
        assertThat(query.getKeyword()).isEqualTo("adm");
        assertThat(query.getStatus()).isEqualTo("active");
        assertThat(query.getPageNum()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);

        PageResult<RoleEntity> result = response.getData();
        assertThat(result.getList()).containsExactly(page.getRecords().get(0));
        assertThat(result.getTotal()).isEqualTo(35L);
        assertThat(result.getPage()).isEqualTo(2L);
        assertThat(result.getSize()).isEqualTo(20L);
    }

    @Test
    void pageShouldReturnEmptyListWhenNoRecord() {
        // 边界场景：空分页结果
        Page<RoleEntity> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        empty.setTotal(0L);
        when(roleService.pageQuery(any(RoleQueryDTO.class))).thenReturn(empty);

        R<PageResult<RoleEntity>> response = controller.page(null, null, 1, 10);

        assertThat(response.getData().getList()).isEmpty();
        assertThat(response.getData().getTotal()).isZero();
    }

    @Test
    void assignUsersShouldDelegateAndReturnOk() {
        // 正常场景：给角色分配用户
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r5");
        dto.setUserIds(List.of("u1", "u2"));

        R<Void> response = controller.assignUsers(dto);

        verify(roleService).assignUsersToRole(dto);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void listRolesByUserShouldDelegate() {
        // 正常场景：按用户查询角色列表
        List<RoleEntity> roles = List.of(role("r6", "dev"), role("r7", "qa"));
        when(roleService.listRolesByUserId("u9")).thenReturn(roles);

        assertThat(controller.listRolesByUser("u9").getData()).isEqualTo(roles);
        verify(roleService).listRolesByUserId("u9");
    }

    @Test
    void listRolesByUserShouldReturnEmptyList() {
        // 边界场景：用户无角色
        when(roleService.listRolesByUserId("u10")).thenReturn(List.of());

        assertThat(controller.listRolesByUser("u10").getData()).isEmpty();
    }
}
