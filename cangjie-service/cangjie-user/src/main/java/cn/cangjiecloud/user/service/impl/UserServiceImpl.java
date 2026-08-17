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
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.service.IUserService;
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
        UserIdentity identity = buildIdentity(user);
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
