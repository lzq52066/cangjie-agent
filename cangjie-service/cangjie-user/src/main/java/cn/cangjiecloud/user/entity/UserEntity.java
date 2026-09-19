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
    /** 是否为待修改的初始密码：true 时首次登录被强制要求修改密码 */
    private Boolean mustChangePassword;
}
