package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
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
}