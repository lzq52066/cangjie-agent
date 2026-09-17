package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoleEntity} 单元测试：访问器、equals/hashCode/toString 与表名注解。
 */
class RoleEntityTest {

    private static RoleEntity sample() {
        RoleEntity role = new RoleEntity();
        role.setId("r1");
        role.setName("运维");
        role.setCode("ops");
        role.setDescription("运维角色");
        role.setStatus("active");
        return role;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        RoleEntity role = sample();

        assertThat(role.getId()).isEqualTo("r1");
        assertThat(role.getName()).isEqualTo("运维");
        assertThat(role.getCode()).isEqualTo("ops");
        assertThat(role.getDescription()).isEqualTo("运维角色");
        assertThat(role.getStatus()).isEqualTo("active");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：未赋值时为 null
        RoleEntity role = new RoleEntity();

        assertThat(role.getCode()).isNull();
        assertThat(role.getStatus()).isNull();
        assertThat(role.getDeleted()).isNull();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllFields() {
        // 正常场景：值对象语义
        RoleEntity one = sample();
        RoleEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);

        another.setStatus("inactive");
        assertThat(one).isNotEqualTo(another);
    }

    @Test
    void toStringShouldContainCode() {
        // 正常场景：toString 输出包含角色编码
        assertThat(sample().toString()).contains("code=ops");
    }

    @Test
    void tableNameShouldBeRole() {
        // 正常场景：表名注解映射到 role 表
        TableName tableName = RoleEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("role");
    }
}
