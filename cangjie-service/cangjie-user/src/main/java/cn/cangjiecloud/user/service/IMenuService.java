package cn.cangjiecloud.user.service;

import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 菜单服务接口
 */
public interface IMenuService extends IService<MenuEntity> {

    /**
     * 创建菜单
     */
    MenuEntity create(MenuDTO dto);

    /**
     * 更新菜单
     */
    MenuEntity update(String id, MenuDTO dto);

    /**
     * 删除菜单
     */
    void delete(String id);

    /**
     * 根据角色 ID 查询菜单列表
     */
    List<MenuEntity> listMenusByRoleId(String roleId);

    /**
     * 给角色分配菜单
     */
    void assignMenusToRole(AssignMenuDTO dto);
}