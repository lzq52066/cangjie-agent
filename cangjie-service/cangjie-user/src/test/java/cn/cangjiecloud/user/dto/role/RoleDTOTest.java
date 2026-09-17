package cn.cangjiecloud.user.dto.role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoleDTO} 单元测试：读写与值对象语义。
 */
class RoleDTOTest {

    private static RoleDTO sample() {
        RoleDTO dto = new RoleDTO();
        dto.setName("运维");
        dto.setCode("ops");
        dto.setDescription("负责运维事务");
        dto.setStatus("active");
        return dto;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        RoleDTO dto = sample();

        assertThat(dto.getName()).isEqualTo("运维");
        assertThat(dto.getCode()).isEqualTo("ops");
        assertThat(dto.getDescription()).isEqualTo("负责运维事务");
        assertThat(dto.getStatus()).isEqualTo("active");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：空对象（Service 层依赖 null 判断决定是否覆盖旧值）
        RoleDTO dto = new RoleDTO();

        assertThat(dto.getName()).isNull();
        assertThat(dto.getCode()).isNull();
        assertThat(dto.getDescription()).isNull();
        assertThat(dto.getStatus()).isNull();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        RoleDTO one = sample();
        RoleDTO another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setStatus("inactive");
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("code=ops");
    }
}
