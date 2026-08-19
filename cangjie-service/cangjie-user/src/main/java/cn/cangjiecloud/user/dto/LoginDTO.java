package cn.cangjiecloud.user.dto;

import cn.cangjiecloud.common.annotation.Sensitive;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Sensitive
    @NotBlank(message = "密码不能为空")
    private String password;

    private String code;
}
