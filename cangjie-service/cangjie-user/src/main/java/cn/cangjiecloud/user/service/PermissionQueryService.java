package cn.cangjiecloud.user.service;

import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.cangjiecloud.user.mapper.RoleMapper;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 用户权限查询与缓存服务。
 * <p>
 * 集中处理用户-角色-菜单-权限的关联查询，并通过 Spring Cache (caffeine) 缓存查询结果，
 * 避免登录和每次 Sa-Token 鉴权都全量查库。角色/菜单授权变更时调用 {@link #evictAll()} 清空缓存。
 * </p>
 * <p>
 * <b>超级管理员旁路</b>：持有 ADMIN 角色的用户不受权限矩阵限制，直接返回全量菜单和权限码。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionQueryService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;

    private static final Comparator<MenuEntity> SORT_COMPARATOR =
            Comparator.comparing(MenuEntity::getSort,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    /** 判断用户是否为超级管理员 */
    private boolean isAdmin(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user != null && AppConst.ROLE_ADMIN.equals(user.getRole())) return true;
        List<String> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId)
                        .eq(UserRoleEntity::getDeleted, 0))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) return false;
        return roleMapper.selectCount(
                new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, roleIds)
                        .eq(RoleEntity::getCode, AppConst.ROLE_ADMIN)
                        .eq(RoleEntity::getDeleted, 0)) > 0;
    }

    /** 查询全量按钮权限码（ADMIN 旁路） */
    private List<String> getAllPermissions() {
        return menuMapper.selectList(
                new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getDeleted, 0))
                .stream()
                .filter(m -> "button".equals(m.getType()))
                .map(MenuEntity::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** 查询全量菜单（ADMIN 旁路） */
    private List<MenuVO> getAllMenus() {
        return menuMapper.selectList(
                new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getDeleted, 0))
                .stream()
                .sorted(SORT_COMPARATOR)
                .map(m -> {
                    MenuVO vo = new MenuVO();
                    vo.setId(m.getId());
                    vo.setName(m.getName());
                    vo.setPath(m.getPath());
                    vo.setComponent(m.getComponent());
                    vo.setIcon(m.getIcon());
                    vo.setType(m.getType());
                    vo.setStatus(m.getStatus());
                    vo.setSort(m.getSort());
                    vo.setParentId(m.getParentId());
                    return vo;
                })
                .toList();
    }

    /**
     * 查询用户拥有的按钮级权限码列表（type=button 的菜单 code）。
     * ADMIN 角色用户直接返回全量权限。
     */
    @Cacheable(cacheNames = "user:permissions", key = "#userId")
    public List<String> getPermissionsByUserId(String userId) {
        if (isAdmin(userId)) return getAllPermissions();
        List<String> menuIds = findMenuIdsByUserId(userId);
        if (menuIds.isEmpty()) return List.of();
        return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .in(MenuEntity::getId, menuIds)
                        .eq(MenuEntity::getDeleted, 0))
                .stream()
                .filter(m -> "button".equals(m.getType()))
                .map(MenuEntity::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询用户拥有的角色编码列表（role.code）。
     */
    @Cacheable(cacheNames = "user:roles", key = "#userId")
    public List<String> getRoleCodesByUserId(String userId) {
        List<String> roleIds = findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) return List.of();
        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, roleIds)
                        .eq(RoleEntity::getDeleted, 0))
                .stream()
                .map(RoleEntity::getCode)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 查询用户可见的菜单列表（含父子关系，由前端构建树）。
     * ADMIN 角色用户直接返回全量菜单。
     */
    @Cacheable(cacheNames = "user:menus", key = "#userId")
    public List<MenuVO> getMenusByUserId(String userId) {
        if (isAdmin(userId)) return getAllMenus();
        List<String> menuIds = findMenuIdsByUserId(userId);
        if (menuIds.isEmpty()) return List.of();
        return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .in(MenuEntity::getId, menuIds)
                        .eq(MenuEntity::getDeleted, 0))
                .stream()
                .sorted(SORT_COMPARATOR)
                .map(m -> {
                    MenuVO vo = new MenuVO();
                    vo.setId(m.getId());
                    vo.setName(m.getName());
                    vo.setPath(m.getPath());
                    vo.setComponent(m.getComponent());
                    vo.setIcon(m.getIcon());
                    vo.setType(m.getType());
                    vo.setStatus(m.getStatus());
                    vo.setSort(m.getSort());
                    vo.setParentId(m.getParentId());
                    return vo;
                })
                .toList();
    }

    /**
     * 角色/菜单授权变更后调用，清空所有用户的权限缓存。
     */
    @CacheEvict(cacheNames = {"user:permissions", "user:roles", "user:menus"}, allEntries = true)
    public void evictAll() {
        log.info("用户权限缓存已清空");
    }

    private List<String> findRoleIdsByUserId(String userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId)
                        .eq(UserRoleEntity::getDeleted, 0))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
    }

    private List<String> findMenuIdsByUserId(String userId) {
        List<String> roleIds = findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) return List.of();
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .in(RoleMenuEntity::getRoleId, roleIds)
                        .eq(RoleMenuEntity::getDeleted, 0))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .distinct()
                .toList();
    }
}