package cn.cangjiecloud.user.config;

import cn.cangjiecloud.user.service.PermissionQueryService;
import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CangjieStpInterface implements StpInterface {

    private final PermissionQueryService permissionQueryService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionQueryService.getPermissionsByUserId((String) loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return permissionQueryService.getRoleCodesByUserId((String) loginId);
    }
}
