package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.user.dto.assign.AssignRoleDTO;
import cn.cangjiecloud.user.dto.role.RoleDTO;
import cn.cangjiecloud.user.dto.role.RoleQueryDTO;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.service.IRoleService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
    public R<RoleEntity> create(@RequestBody RoleDTO dto) {
        return R.data(roleService.create(dto));
    }

    @PutMapping("/{id}")
    public R<RoleEntity> update(@PathVariable String id, @RequestBody RoleDTO dto) {
        return R.data(roleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        roleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<RoleEntity> get(@PathVariable String id) {
        return R.data(roleService.getById(id));
    }

    @GetMapping
    public R<IPage<RoleEntity>> page(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        RoleQueryDTO query = new RoleQueryDTO();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return R.data(roleService.pageQuery(query));
    }

    @PostMapping("/assignUsers")
    public R<Void> assignUsers(@RequestBody AssignRoleDTO dto) {
        roleService.assignUsersToRole(dto);
        return R.ok();
    }

    @GetMapping("/roles/user/{userId}")
    public R<List<RoleEntity>> listRolesByUser(@PathVariable String userId) {
        return R.data(roleService.listRolesByUserId(userId));
    }
}