package cn.cangjiecloud.user.dto;

import cn.cangjiecloud.common.annotation.Sensitive;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginDTO} 单元测试：读写语义 + 参数校验注解与敏感字段标记。
 */
class LoginDTOTest {

    private static LoginDTO sample() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("plain-text");
        dto.setCode("123456");
        return dto;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        LoginDTO dto = sample();

        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getPassword()).isEqualTo("plain-text");
        assertThat(dto.getCode()).isEqualTo("123456");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：空对象
        LoginDTO dto = new LoginDTO();

        assertThat(dto.getUsername()).isNull();
        assertThat(dto.getPassword()).isNull();
        assertThat(dto.getCode()).isNull();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        LoginDTO one = sample();
        LoginDTO another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setPassword("other");
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("username=alice");
    }

    @Test
    void usernameShouldBeRequired() throws NoSuchFieldException {
        // 正常场景：用户名必填且提示语明确
        NotBlank notBlank = LoginDTO.class.getDeclaredField("username").getAnnotation(NotBlank.class);
        assertThat(notBlank).isNotNull();
        assertThat(notBlank.message()).isEqualTo("用户名不能为空");
    }

    @Test
    void passwordShouldBeRequiredAndSensitive() throws NoSuchFieldException {
        // 正常场景：密码必填且标记为敏感字段（操作日志需过滤）
        Field password = LoginDTO.class.getDeclaredField("password");

        NotBlank notBlank = password.getAnnotation(NotBlank.class);
        assertThat(notBlank).isNotNull();
        assertThat(notBlank.message()).isEqualTo("密码不能为空");
        assertThat(password.getAnnotation(Sensitive.class)).isNotNull();
    }

    @Test
    void codeShouldBeOptional() throws NoSuchFieldException {
        // 边界场景：验证码为可选字段，不加校验注解
        assertThat(LoginDTO.class.getDeclaredField("code").getAnnotation(NotBlank.class)).isNull();
    }
}
