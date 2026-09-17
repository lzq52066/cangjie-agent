package cn.cangjiecloud.user.dto.assign;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssignRoleDTO} 单元测试：读写与值对象语义（Service 层依赖 null/空集合做参数校验）。
 */
class AssignRoleDTOTest {

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：角色与用户列表
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r1");
        dto.setUserIds(List.of("u1", "u2"));

        assertThat(dto.getRoleId()).isEqualTo("r1");
        assertThat(dto.getUserIds()).containsExactly("u1", "u2");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：空对象触发 Service 层"请指定角色和用户列表"校验
        AssignRoleDTO dto = new AssignRoleDTO();

        assertThat(dto.getRoleId()).isNull();
        assertThat(dto.getUserIds()).isNull();
    }

    @Test
    void emptyListShouldBeDistinguishableFromNull() {
        // 边界场景：空集合与 null 都属于非法入参，但语义不同
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setRoleId("r1");
        dto.setUserIds(new ArrayList<>());

        assertThat(dto.getUserIds()).isNotNull().isEmpty();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        AssignRoleDTO one = new AssignRoleDTO();
        one.setRoleId("r1");
        one.setUserIds(List.of("u1"));
        AssignRoleDTO another = new AssignRoleDTO();
        another.setRoleId("r1");
        another.setUserIds(List.of("u1"));

        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setRoleId("r2");
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("roleId=r1");
    }
}
