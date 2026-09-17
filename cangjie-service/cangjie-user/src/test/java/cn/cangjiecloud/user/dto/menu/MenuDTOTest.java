package cn.cangjiecloud.user.dto.menu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MenuDTO} 单元测试：读写与值对象语义（Service 层依据 null 判断决定是否覆盖旧值）。
 */
class MenuDTOTest {

    private static MenuDTO sample() {
        MenuDTO dto = new MenuDTO();
        dto.setName("用户管理");
        dto.setCode("system:user");
        dto.setPath("/system/user");
        dto.setComponent("system/user/index");
        dto.setIcon("user");
        dto.setParentId("0");
        dto.setSort(2);
        dto.setType("menu");
        dto.setStatus("active");
        return dto;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        MenuDTO dto = sample();

        assertThat(dto.getName()).isEqualTo("用户管理");
        assertThat(dto.getCode()).isEqualTo("system:user");
        assertThat(dto.getPath()).isEqualTo("/system/user");
        assertThat(dto.getComponent()).isEqualTo("system/user/index");
        assertThat(dto.getIcon()).isEqualTo("user");
        assertThat(dto.getParentId()).isEqualTo("0");
        assertThat(dto.getSort()).isEqualTo(2);
        assertThat(dto.getType()).isEqualTo("menu");
        assertThat(dto.getStatus()).isEqualTo("active");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：空对象，所有字段（含 sort）为 null
        MenuDTO dto = new MenuDTO();

        assertThat(dto.getName()).isNull();
        assertThat(dto.getCode()).isNull();
        assertThat(dto.getPath()).isNull();
        assertThat(dto.getComponent()).isNull();
        assertThat(dto.getIcon()).isNull();
        assertThat(dto.getParentId()).isNull();
        assertThat(dto.getSort()).isNull();
        assertThat(dto.getType()).isNull();
        assertThat(dto.getStatus()).isNull();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        MenuDTO one = sample();
        MenuDTO another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setSort(null);
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("code=system:user").contains("sort=2");
    }
}
