package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.user.entity.RoleEntity;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.service.IRoleService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/user")
public class UserController {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final IRoleService roleService;

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @GetMapping("/options")
    public R<List<Map<String, Object>>> options(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId, UserEntity::getUsername,
                        UserEntity::getNickname, UserEntity::getEmail, UserEntity::getPhone)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(UserEntity::getUsername, keyword)
                        .or()
                        .like(UserEntity::getNickname, keyword))
                .orderByAsc(UserEntity::getUsername);
        List<UserEntity> users = userMapper.selectList(wrapper);
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            return m;
        }).toList();
        return R.data(result);
    }

    /**
     * 用户分页：按用户名/昵称/邮箱关键词、状态、来源筛选
     */
    @GetMapping
    public R<PageResult<UserEntity>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Boolean isActive,
                                          @RequestParam(required = false) String source,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(UserEntity::getUsername, keyword)
                        .or().like(UserEntity::getNickname, keyword)
                        .or().like(UserEntity::getEmail, keyword)
                        .or().like(UserEntity::getPhone, keyword))
                .eq(isActive != null, UserEntity::getIsActive, isActive)
                .eq(StringUtils.hasText(source), UserEntity::getSource, source)
                .orderByDesc(UserEntity::getCreateTime);
        Page<UserEntity> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        // 密码哈希不允许出现在管理端响应中
        page.getRecords().forEach(u -> u.setPassword(null));
        return R.data(PageResult.of(page));
    }

    /**
     * 用户详情（不含密码），附带角色 ID 列表
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable String id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new ApiException("用户不存在");
        }
        user.setPassword(null);
        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("roleIds", roleService.listRolesByUserId(id).stream().map(RoleEntity::getId).toList());
        return R.data(result);
    }

    /**
     * 新建用户
     */
    @PostMapping
    public R<UserEntity> create(@RequestBody UserSaveDTO dto) {
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new ApiException("用户名与密码不能为空");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new ApiException("用户名已存在");
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(dto.getUsername().trim());
        entity.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        entity.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setRole(AppConst.ROLE_USER);
        entity.setIsActive(dto.getIsActive() == null || dto.getIsActive());
        entity.setSource("LOCAL");
        entity.setLanguage("zh_CN");
        userMapper.insert(entity);
        saveUserRoles(entity.getId(), dto.getRoleIds());
        entity.setPassword(null);
        return R.data(entity);
    }

    /**
     * 编辑用户基础信息（不改密码）
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody UserSaveDTO dto) {
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) {
            throw new ApiException("用户不存在");
        }
        if (StringUtils.hasText(dto.getNickname())) entity.setNickname(dto.getNickname());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
        userMapper.updateById(entity);
        if (dto.getRoleIds() != null) {
            saveUserRoles(id, dto.getRoleIds());
        }
        return R.ok();
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        String password = body == null ? null : body.get("password");
        if (!StringUtils.hasText(password)) {
            throw new ApiException("新密码不能为空");
        }
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) {
            throw new ApiException("用户不存在");
        }
        entity.setPassword(PASSWORD_ENCODER.encode(password));
        userMapper.updateById(entity);
        return R.ok();
    }

    /**
     * 启用 / 停用
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body == null ? null : body.get("isActive");
        if (active == null) {
            throw new ApiException("状态不能为空");
        }
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) {
            throw new ApiException("用户不存在");
        }
        entity.setIsActive(active);
        userMapper.updateById(entity);
        return R.ok();
    }

    /**
     * 全量覆盖用户的角色绑定（与编辑表单共用）
     */
    private void saveUserRoles(String userId, List<String> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (String roleId : roleIds) {
            UserRoleEntity ur = new UserRoleEntity();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /**
     * 用户新建/编辑入参
     */
    @lombok.Data
    public static class UserSaveDTO {
        private String username;
        private String password;
        private String nickname;
        private String email;
        private String phone;
        private Boolean isActive;
        private List<String> roleIds;
    }
}
