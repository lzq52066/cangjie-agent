package cn.cangjiecloud.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户自助修改个人资料入参
 */
@Data
public class ProfileUpdateDTO {

    @Size(max = 64, message = "昵称长度不能超过 64 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;
}
