package cn.cangjiecloud.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.common.domain.UserIdentity;
import cn.cangjiecloud.common.domain.MenuVO;
import cn.cangjiecloud.user.dto.LoginDTO;
import cn.cangjiecloud.user.entity.UserEntity;

import java.util.List;
import java.util.Map;

public interface IUserService extends IService<UserEntity> {

    Map<String, Object> login(LoginDTO dto);

    List<String> getPermissionsByUserId(String userId);

    List<String> getRolesByUserId(String userId);

    List<MenuVO> getMenusByUserId(String userId);

    void logout();

    UserIdentity getCurrentIdentity();

    UserEntity getByUsername(String username);
}
