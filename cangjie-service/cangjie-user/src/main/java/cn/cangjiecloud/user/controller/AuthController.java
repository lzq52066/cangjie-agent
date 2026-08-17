package cn.cangjiecloud.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    @PostMapping(AppConst.OPEN_API + "/auth/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return R.data(userService.login(dto));
    }

    @PostMapping(AppConst.OPEN_API + "/auth/logout")
    public R<Void> logout() {
        userService.logout();
        return R.ok();
    }

    @SaCheckLogin
    @GetMapping(AppConst.ADMIN_API + "/user/info")
    public R<UserIdentity> info() {
        return R.data(userService.getCurrentIdentity());
    }

    @SaCheckLogin
    @GetMapping(AppConst.ADMIN_API + "/auth/keep-alive")
    public R<Boolean> keepAlive() {
        return R.data(true);
    }
}
