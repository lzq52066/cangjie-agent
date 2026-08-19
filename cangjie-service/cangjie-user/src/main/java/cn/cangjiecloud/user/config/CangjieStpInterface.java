package cn.cangjiecloud.user.config;

import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.entity.RoleMenuEntity;
import cn.cangjiecloud.user.entity.UserRoleEntity;
import cn.cangjiecloud.user.service.IUserService;
import cn.cangjiecloud.user.mapper.RoleMenuMapper;
import cn.cangjiecloud.user.mapper.UserRoleMapper;
import cn.cangjiecloud.user.mapper.MenuMapper;
import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CangjieStpInterface implements StpInterface {

    @Autowired
    private IUserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String userId = (String) loginId;
        List<String> perms = userService.getPermissionsByUserId(userId);
        return perms != null ? perms : new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String userId = (String) loginId;
        List<String> roles = userService.getRolesByUserId(userId);
        return roles != null ? roles : new ArrayList<>();
    }
}