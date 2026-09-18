package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.dto.PasswordChangeDTO;
import cn.cangjiecloud.user.dto.ProfileUpdateDTO;
import cn.cangjiecloud.user.service.IUserService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人中心：当前登录用户自助查看/修改资料、修改密码
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConst.ADMIN_API + "/profile")
public class ProfileController {

    private final IUserService userService;

    @GetMapping
    public R<UserIdentity> info() {
        return R.data(userService.getCurrentIdentity());
    }

    @PutMapping
    public R<UserIdentity> update(@Valid @RequestBody ProfileUpdateDTO dto) {
        return R.data(userService.updateCurrentProfile(dto));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        userService.changeCurrentPassword(dto);
        return R.ok();
    }
}
