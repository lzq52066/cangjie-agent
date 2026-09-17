package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserRoleEntity} 单元测试：用户角色关联实体的读写与值对象语义。
 */
class UserRoleEntityTest {

    private static UserRoleEntity sample() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setId("ur1");
        entity.setUserId("u1");
        entity.setRoleId("r1");
        entity.setCreateBy("system");
        entity.setDeleted(0);
        return entity;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：关联字段读写
        UserRoleEntity entity = sample();

        assertThat(entity.getUserId()).isEqualTo("u1");
        assertThat(entity.getRoleId()).isEqualTo("r1");
        assertThat(entity.getId()).isEqualTo("ur1");
        assertThat(entity.getCreateBy()).isEqualTo("system");
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：新建对象字段为空
        UserRoleEntity entity = new UserRoleEntity();

        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getRoleId()).isNull();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllFields() {
        // 正常场景：值对象语义
        UserRoleEntity one = sample();
        UserRoleEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);

        another.setRoleId("r2");
        assertThat(one).isNotEqualTo(another);
    }

    @Test
    void toStringShouldContainUserAndRole() {
        // 正常场景：toString 输出关联字段
        assertThat(sample().toString()).contains("userId=u1").contains("roleId=r1");
    }

    @Test
    void tableNameShouldBeUserRole() {
        // 正常场景：表名注解映射到 user_role 表
        TableName tableName = UserRoleEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("user_role");
    }
}
