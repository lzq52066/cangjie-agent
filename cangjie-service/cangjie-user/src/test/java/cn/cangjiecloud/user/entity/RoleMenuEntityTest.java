package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoleMenuEntity} 单元测试：角色菜单关联实体的读写与值对象语义。
 */
class RoleMenuEntityTest {

    private static RoleMenuEntity sample() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setId("rm1");
        entity.setRoleId("r1");
        entity.setMenuId("m1");
        entity.setUpdateBy("admin");
        entity.setDeleted(0);
        return entity;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：关联字段读写
        RoleMenuEntity entity = sample();

        assertThat(entity.getRoleId()).isEqualTo("r1");
        assertThat(entity.getMenuId()).isEqualTo("m1");
        assertThat(entity.getId()).isEqualTo("rm1");
        assertThat(entity.getUpdateBy()).isEqualTo("admin");
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：新建对象字段为空
        RoleMenuEntity entity = new RoleMenuEntity();

        assertThat(entity.getRoleId()).isNull();
        assertThat(entity.getMenuId()).isNull();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllFields() {
        // 正常场景：值对象语义
        RoleMenuEntity one = sample();
        RoleMenuEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);

        another.setMenuId("m2");
        assertThat(one).isNotEqualTo(another);
    }

    @Test
    void toStringShouldContainRoleAndMenu() {
        // 正常场景：toString 输出关联字段
        assertThat(sample().toString()).contains("roleId=r1").contains("menuId=m1");
    }

    @Test
    void tableNameShouldBeRoleMenu() {
        // 正常场景：表名注解映射到 role_menu 表
        TableName tableName = RoleMenuEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("role_menu");
    }
}
