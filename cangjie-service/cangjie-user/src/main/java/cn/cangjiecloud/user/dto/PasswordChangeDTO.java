package cn.cangjiecloud.user.dto;

import cn.cangjiecloud.common.annotation.Sensitive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户自助修改密码入参（需校验旧密码）
 */
@Data
public class PasswordChangeDTO {

    @Sensitive
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @Sensitive
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6-64 个字符之间")
    private String newPassword;
}
