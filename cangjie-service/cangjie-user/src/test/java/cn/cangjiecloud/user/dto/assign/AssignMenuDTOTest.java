package cn.cangjiecloud.user.dto.assign;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssignMenuDTO} 单元测试：读写与值对象语义（Service 层依赖 null/空集合做参数校验）。
 */
class AssignMenuDTOTest {

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：角色与菜单列表
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setRoleId("r1");
        dto.setMenuIds(List.of("m1", "m2"));

        assertThat(dto.getRoleId()).isEqualTo("r1");
        assertThat(dto.getMenuIds()).containsExactly("m1", "m2");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：空对象触发 Service 层"请指定角色和菜单列表"校验
        AssignMenuDTO dto = new AssignMenuDTO();

        assertThat(dto.getRoleId()).isNull();
        assertThat(dto.getMenuIds()).isNull();
    }

    @Test
    void emptyListShouldBeDistinguishableFromNull() {
        // 边界场景：空集合与 null 都属于非法入参，但语义不同
        AssignMenuDTO dto = new AssignMenuDTO();
        dto.setMenuIds(new ArrayList<>());

        assertThat(dto.getMenuIds()).isNotNull().isEmpty();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        AssignMenuDTO one = new AssignMenuDTO();
        one.setRoleId("r1");
        one.setMenuIds(List.of("m1"));
        AssignMenuDTO another = new AssignMenuDTO();
        another.setRoleId("r1");
        another.setMenuIds(List.of("m1"));

        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setMenuIds(List.of("m1", "m2"));
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("roleId=r1");
    }
}
