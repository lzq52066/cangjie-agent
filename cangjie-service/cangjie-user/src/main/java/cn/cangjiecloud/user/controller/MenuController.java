package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.service.IMenuService;
import cn.dev33.satoken.annotation.SaCheckLogin;
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
    public R<MenuEntity> create(@RequestBody MenuDTO dto) {
        return R.data(menuService.create(dto));
    }

    @PutMapping("/{id}")
    public R<MenuEntity> update(@PathVariable String id, @RequestBody MenuDTO dto) {
        return R.data(menuService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        menuService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<MenuEntity> get(@PathVariable String id) {
        return R.data(menuService.getById(id));
    }

    @GetMapping
    public R<List<MenuEntity>> list() {
        return R.data(menuService.list());
    }

    @PostMapping("/assignMenus")
    public R<Void> assignMenus(@RequestBody AssignMenuDTO dto) {
        menuService.assignMenusToRole(dto);
        return R.ok();
    }

    @GetMapping("/role/{roleId}")
    public R<List<MenuEntity>> listMenusByRole(@PathVariable String roleId) {
        return R.data(menuService.listMenusByRoleId(roleId));
    }
}