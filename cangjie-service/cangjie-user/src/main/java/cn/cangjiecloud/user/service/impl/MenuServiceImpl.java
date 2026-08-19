package cn.cangjiecloud.user.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.user.dto.assign.AssignMenuDTO;
import cn.cangjiecloud.user.dto.menu.MenuDTO;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.service.IMenuService;
import cn.cangjiecloud.user.service.PermissionQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, MenuEntity>
        implements IMenuService {

    private final RoleMenuMapper roleMenuMapper;
    private final PermissionQueryService permissionQueryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuEntity create(MenuDTO dto) {
        if (!StringUtils.hasText(dto.getStatus())) {
            dto.setStatus("active");
        }
        if (dto.getSort() == null) {
            dto.setSort(0);
        }
        if (!StringUtils.hasText(dto.getType())) {
            dto.setType("menu");
        }
        MenuEntity entity = new MenuEntity();
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setPath(dto.getPath());
        entity.setComponent(dto.getComponent());
        entity.setIcon(dto.getIcon());
        entity.setParentId(dto.getParentId());
        entity.setSort(dto.getSort());
        entity.setType(dto.getType());
        entity.setStatus(dto.getStatus());
        save(entity);
        log.info("菜单已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuEntity update(String id, MenuDTO dto) {
        MenuEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("菜单不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getCode())) entity.setCode(dto.getCode());
        if (StringUtils.hasText(dto.getPath())) entity.setPath(dto.getPath());
        if (StringUtils.hasText(dto.getComponent())) entity.setComponent(dto.getComponent());
        if (StringUtils.hasText(dto.getIcon())) entity.setIcon(dto.getIcon());
        if (StringUtils.hasText(dto.getParentId())) entity.setParentId(dto.getParentId());
        if (dto.getSort() != null) entity.setSort(dto.getSort());
        if (StringUtils.hasText(dto.getType())) entity.setType(dto.getType());
        if (StringUtils.hasText(dto.getStatus())) entity.setStatus(dto.getStatus());
        updateById(entity);
        log.info("菜单已更新: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        MenuEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("菜单不存在");
        }
        // 删除关联的角色菜单关系
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>()
                .eq(RoleMenuEntity::getMenuId, id));
        removeById(id);
        permissionQueryService.evictAll();
        log.info("菜单已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<MenuEntity> listMenusByRoleId(String roleId) {
        List<RoleMenuEntity> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenuEntity>()
                        .eq(RoleMenuEntity::getRoleId, roleId));
        List<String> menuIds = roleMenus.stream()
                .map(RoleMenuEntity::getMenuId)
                .collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return List.<MenuEntity>of();
        }
        return listByIds(menuIds);
    }

    @Override
    public List<MenuEntity> tree() {
        List<MenuEntity> all = list(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getDeleted, 0));
        Map<String, List<MenuEntity>> parentMap = all.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(MenuEntity::getParentId));
        List<MenuEntity> roots = new ArrayList<>();
        for (MenuEntity m : all) {
            if (m.getParentId() == null || m.getParentId().isEmpty()) {
                roots.add(m);
            }
            List<MenuEntity> children = parentMap.get(m.getId());
            if (children != null) {
                children.sort(Comparator.comparing(MenuEntity::getSort,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
                m.setChildren(children);
            }
        }
        roots.sort(Comparator.comparing(MenuEntity::getSort,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenusToRole(AssignMenuDTO dto) {
        if (dto.getRoleId() == null || dto.getMenuIds() == null || dto.getMenuIds().isEmpty()) {
            throw new ApiException("请指定角色和菜单列表");
        }
        // 删除该角色下原有的菜单关联
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>()
                .eq(RoleMenuEntity::getRoleId, dto.getRoleId()));
        // 批量新增
        for (String mid : dto.getMenuIds()) {
            RoleMenuEntity rm = new RoleMenuEntity();
            rm.setMenuId(mid);
            rm.setRoleId(dto.getRoleId());
            roleMenuMapper.insert(rm);
        }
        permissionQueryService.evictAll();
        log.info("角色 {} 已重新分配 {} 个菜单", dto.getRoleId(), dto.getMenuIds().size());
    }
}