package cn.cangjiecloud.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.api.ResultCode;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.props.SystemProperties;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.entity.MenuEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.cangjiecloud.user.service.IUserService;
import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements IUserService {

    private final SystemProperties systemProperties;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(LoginDTO dto) {
        ensureDefaultAdminExists();
        UserEntity user = getByUsername(dto.getUsername());
        if (user == null) {
            throw new ApiException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ResultCode.NO_PERMISSION, "账号已禁用");
        }
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new ApiException("用户名或密码错误");
        }
        // 查询并缓存用户权限
        List<String> permissions = getPermissionsByUserId(user.getId());
        List<String> roles = getRolesByUserId(user.getId());
        List<MenuVO> menus = getMenusByUserId(user.getId());

        UserIdentity identity = buildIdentity(user);
        identity.setPermissions(permissions);
        identity.setMenus(menus);

        UserContext.setUserId(user.getId());
        UserContext.setIdentity(identity);

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("tokenName", StpUtil.getTokenName());
        result.put("user", identity);
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserIdentity getCurrentIdentity() {
        UserIdentity identity = UserContext.getIdentity();
        if (identity != null) return identity;
        String uid = UserContext.getUserId();
        if (uid == null) return null;
        UserEntity user = getById(uid);
        if (user == null) return null;
        UserIdentity idt = buildIdentity(user);
        UserContext.setIdentity(idt);
        return idt;
    }

    @Override
    public UserEntity getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    }

    @Override
    public List<String> getPermissionsByUserId(String userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getDeleted, 0)
        );
        if (userRoles.isEmpty()) return List.of();
        List<String> roleIds = userRoles.stream().map(UserRoleEntity::getRoleId).toList();
        List<RoleMenuEntity> roleMenus = roleMenuMapper.selectList(
            new LambdaQueryWrapper<RoleMenuEntity>()
                .in(RoleMenuEntity::getRoleId, roleIds)
                .eq(RoleMenuEntity::getDeleted, 0)
        );
        if (roleMenus.isEmpty()) return List.of();
        List<String> menuIds = roleMenus.stream().map(RoleMenuEntity::getMenuId).distinct().toList();
        List<MenuEntity> menus = menuMapper.selectList(
            new LambdaQueryWrapper<MenuEntity>()
                .in(MenuEntity::getId, menuIds)
                .eq(MenuEntity::getDeleted, 0)
        );
        return menus.stream()
            .filter(m -> "button".equals(m.getType()))
            .map(MenuEntity::getCode)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Override
    public List<String> getRolesByUserId(String userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getDeleted, 0)
        );
        return userRoles.stream()
            .map(UserRoleEntity::getRoleId)
            .toList();
    }

    @Override
    public List<MenuVO> getMenusByUserId(String userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getDeleted, 0)
        );
        if (userRoles.isEmpty()) return List.of();
        List<String> roleIds = userRoles.stream().map(UserRoleEntity::getRoleId).toList();
        List<RoleMenuEntity> roleMenus = roleMenuMapper.selectList(
            new LambdaQueryWrapper<RoleMenuEntity>()
                .in(RoleMenuEntity::getRoleId, roleIds)
                .eq(RoleMenuEntity::getDeleted, 0)
        );
        if (roleMenus.isEmpty()) return List.of();
        List<String> menuIds = roleMenus.stream().map(RoleMenuEntity::getMenuId).distinct().toList();
        List<MenuEntity> menus = menuMapper.selectList(
            new LambdaQueryWrapper<MenuEntity>()
                .in(MenuEntity::getId, menuIds)
                .eq(MenuEntity::getDeleted, 0)
                .orderByAsc(MenuEntity::getSort)
        );
        return menus.stream()
            .sorted(java.util.Comparator.comparing(MenuEntity::getSort,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
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
                return vo;
            })
            .toList();
    }

    private UserIdentity buildIdentity(UserEntity user) {
        return UserIdentity.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .tenantId(user.getTenantId() == null ? AppConst.DEFAULT_TENANT_ID : user.getTenantId())
                .workspaceId(AppConst.Workspace.DEFAULT_WORKSPACE_ID)
                .build();
    }

    private void ensureDefaultAdminExists() {
        long count = count(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, systemProperties.getDefaultUsername()));
        if (count > 0) return;
        String tenantId = AppConst.DEFAULT_TENANT_ID;
        UserEntity admin = new UserEntity();
        admin.setUsername(systemProperties.getDefaultUsername());
        admin.setPassword(PASSWORD_ENCODER.encode(systemProperties.getDefaultPassword()));
        admin.setNickname("超级管理员");
        admin.setEmail(systemProperties.getDefaultEmail());
        admin.setPhone(systemProperties.getDefaultPhone());
        admin.setRole(AppConst.ROLE_ADMIN);
        admin.setIsActive(true);
        admin.setSource("LOCAL");
        admin.setLanguage("zh_CN");
        admin.setTenantId(tenantId);
        admin.setCreateBy("system");
        admin.setUpdateBy("system");
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        admin.setDeleted(0);
        save(admin);
        log.info("默认管理员账号已创建: {}", admin.getUsername());
    }
}
