package cn.cangjiecloud.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.api.ResultCode;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.props.SystemProperties;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.dto.PasswordChangeDTO;
import cn.cangjiecloud.user.dto.ProfileUpdateDTO;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.service.IUserService;
import cn.cangjiecloud.user.service.PermissionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements IUserService {

    private final SystemProperties systemProperties;
    private final PermissionQueryService permissionQueryService;
    private final UserRoleMapper userRoleMapper;
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
        UserIdentity identity = buildIdentityWithPermission(user);

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
        UserIdentity idt = buildIdentityWithPermission(user);
        UserContext.setIdentity(idt);
        return idt;
    }

    @Override
    public UserEntity getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserIdentity updateCurrentProfile(ProfileUpdateDTO dto) {
        UserEntity user = requireCurrentUser();
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname().trim());
        }
        user.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : null);
        user.setPhone(StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : null);
        updateById(user);
        UserContext.clearIdentity();
        return getCurrentIdentity();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeCurrentPassword(PasswordChangeDTO dto) {
        UserEntity user = requireCurrentUser();
        if (!PASSWORD_ENCODER.matches(dto.getOldPassword(), user.getPassword())) {
            throw new ApiException("旧密码不正确");
        }
        if (PASSWORD_ENCODER.matches(dto.getNewPassword(), user.getPassword())) {
            throw new ApiException("新密码不能与旧密码相同");
        }
        user.setPassword(PASSWORD_ENCODER.encode(dto.getNewPassword()));
        updateById(user);
    }

    private UserEntity requireCurrentUser() {
        String uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ResultCode.UN_AUTHORIZED, "未登录或登录已过期");
        }
        UserEntity user = getById(uid);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ResultCode.UN_AUTHORIZED, "用户不存在或已被禁用");
        }
        return user;
    }

    private UserIdentity buildIdentityWithPermission(UserEntity user) {
        UserIdentity identity = buildIdentity(user);
        identity.setPermissions(permissionQueryService.getPermissionsByUserId(user.getId()));
        identity.setMenus(permissionQueryService.getMenusByUserId(user.getId()));
        return identity;
    }

    private UserIdentity buildIdentity(UserEntity user) {
        return UserIdentity.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .workspaceId(AppConst.Workspace.DEFAULT_WORKSPACE_ID)
                .build();
    }

    private void ensureDefaultAdminExists() {
        long count = count(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, systemProperties.getDefaultUsername()));
        if (count > 0) return;
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
        admin.setCreateBy("system");
        admin.setUpdateBy("system");
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        admin.setDeleted(0);
        save(admin);
        // 绑定默认 ADMIN 角色，保证超级管理员具备完整权限
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(admin.getId());
        userRole.setRoleId("role_admin");
        userRole.setCreateBy("system");
        userRole.setUpdateBy("system");
        userRoleMapper.insert(userRole);
        log.info("默认管理员账号已创建并绑定 ADMIN 角色: {}", admin.getUsername());
    }
}
