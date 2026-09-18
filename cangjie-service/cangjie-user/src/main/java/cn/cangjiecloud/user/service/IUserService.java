package cn.cangjiecloud.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.dto.PasswordChangeDTO;
import cn.cangjiecloud.user.dto.ProfileUpdateDTO;
import cn.cangjiecloud.user.entity.UserEntity;

import java.util.Map;

public interface IUserService extends IService<UserEntity> {

    Map<String, Object> login(LoginDTO dto);

    void logout();

    UserIdentity getCurrentIdentity();

    UserEntity getByUsername(String username);

    /** 当前登录用户自助修改昵称/邮箱/手机，返回更新后的身份信息 */
    UserIdentity updateCurrentProfile(ProfileUpdateDTO dto);

    /** 当前登录用户校验旧密码后修改密码 */
    void changeCurrentPassword(PasswordChangeDTO dto);
}
