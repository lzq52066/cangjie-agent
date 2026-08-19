package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.service.IRoleService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/role")
public class RoleController {

    private final IRoleService roleService;

    @PostMapping
    @SaCheckPermission("system:role:add")
    public R<RoleEntity> create(@RequestBody RoleDTO dto) {
        return R.data(roleService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:role:update")
    public R<RoleEntity> update(@PathVariable String id, @RequestBody RoleDTO dto) {
        return R.data(roleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:delete")
    public R<Void> delete(@PathVariable String id) {
        roleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:role:query")
    public R<RoleEntity> get(@PathVariable String id) {
        return R.data(roleService.getById(id));
    }

    @GetMapping
    @SaCheckPermission("system:role:query")
    public R<PageResult<RoleEntity>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        RoleQueryDTO query = new RoleQueryDTO();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return R.data(PageResult.of(roleService.pageQuery(query)));
    }

    @PostMapping("/assignUsers")
    @SaCheckPermission("system:role:assign")
    public R<Void> assignUsers(@RequestBody AssignRoleDTO dto) {
        roleService.assignUsersToRole(dto);
        return R.ok();
    }

    @GetMapping("/roles/user/{userId}")
    @SaCheckPermission("system:role:query")
    public R<List<RoleEntity>> listRolesByUser(@PathVariable String userId) {
        return R.data(roleService.listRolesByUserId(userId));
    }
}