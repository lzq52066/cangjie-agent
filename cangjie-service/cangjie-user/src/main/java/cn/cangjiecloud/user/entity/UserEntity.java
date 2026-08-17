package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.cangjiecloud.common.mp.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "user", autoResultMap = true)
public class UserEntity extends BaseEntity {

    private String email;
    private String phone;
    private String nickname;
    private String username;
    private String password;
    private String role;
    private Boolean isActive;
    private String source;
    private String language;
    private String avatar;
}
