package cn.cangjiecloud.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.entity.UserEntity;

import java.util.Map;

public interface IUserService extends IService<UserEntity> {

    Map<String, Object> login(LoginDTO dto);

    void logout();

    UserIdentity getCurrentIdentity();

    UserEntity getByUsername(String username);
}
