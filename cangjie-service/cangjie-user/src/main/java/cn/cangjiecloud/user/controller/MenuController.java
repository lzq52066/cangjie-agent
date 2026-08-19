package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.service.IMenuService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/menu")
public class MenuController {

    private final IMenuService menuService;

    @PostMapping
    @SaCheckPermission("system:menu:add")
    public R<MenuEntity> create(@RequestBody MenuDTO dto) {
        return R.data(menuService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:menu:update")
    public R<MenuEntity> update(@PathVariable String id, @RequestBody MenuDTO dto) {
        return R.data(menuService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    public R<Void> delete(@PathVariable String id) {
        menuService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:menu:query")
    public R<MenuEntity> get(@PathVariable String id) {
        return R.data(menuService.getById(id));
    }

    @GetMapping
    @SaCheckPermission("system:menu:query")
    public R<List<MenuEntity>> list() {
        return R.data(menuService.list());
    }

    @GetMapping("/tree")
    @SaCheckPermission("system:menu:query")
    public R<List<MenuEntity>> tree() {
        return R.data(menuService.tree());
    }

    @PostMapping("/assignMenus")
    @SaCheckPermission("system:menu:assign")
    public R<Void> assignMenus(@RequestBody AssignMenuDTO dto) {
        menuService.assignMenusToRole(dto);
        return R.ok();
    }

    @GetMapping("/role/{roleId}")
    @SaCheckPermission("system:menu:query")
    public R<List<MenuEntity>> listMenusByRole(@PathVariable String roleId) {
        return R.data(menuService.listMenusByRoleId(roleId));
    }
}