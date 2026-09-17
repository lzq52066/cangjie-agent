package cn.cangjiecloud.user.dto.role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoleQueryDTO} 单元测试：默认分页参数与读写语义。
 */
class RoleQueryDTOTest {

    @Test
    void defaultPaginationShouldBeFirstPageOfTen() {
        // 边界场景：未显式赋值时默认第 1 页、每页 10 条
        RoleQueryDTO dto = new RoleQueryDTO();

        assertThat(dto.getPageNum()).isEqualTo(1);
        assertThat(dto.getPageSize()).isEqualTo(10);
        assertThat(dto.getKeyword()).isNull();
        assertThat(dto.getStatus()).isNull();
    }

    @Test
    void settersShouldOverrideDefaults() {
        // 正常场景：显式覆盖分页参数
        RoleQueryDTO dto = new RoleQueryDTO();
        dto.setKeyword("adm");
        dto.setStatus("inactive");
        dto.setPageNum(3);
        dto.setPageSize(50);

        assertThat(dto.getKeyword()).isEqualTo("adm");
        assertThat(dto.getStatus()).isEqualTo("inactive");
        assertThat(dto.getPageNum()).isEqualTo(3);
        assertThat(dto.getPageSize()).isEqualTo(50);
    }

    @Test
    void paginationCanBeExplicitlyNulled() {
        // 边界场景：显式置空后由 Service 层回退默认分页
        RoleQueryDTO dto = new RoleQueryDTO();
        dto.setPageNum(null);
        dto.setPageSize(null);

        assertThat(dto.getPageNum()).isNull();
        assertThat(dto.getPageSize()).isNull();
    }

    @Test
    void equalsHashCodeToStringShouldFollowValueSemantics() {
        // 正常场景：Lombok @Data 生成值对象语义
        RoleQueryDTO one = new RoleQueryDTO();
        RoleQueryDTO another = new RoleQueryDTO();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        one.setKeyword("x");
        assertThat(one).isNotEqualTo(another);
        assertThat(one.toString()).contains("keyword=x");
    }
}
