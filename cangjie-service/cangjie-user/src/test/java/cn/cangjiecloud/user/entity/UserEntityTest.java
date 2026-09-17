package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserEntity} 单元测试：验证 Lombok 生成的访问器/equals/hashCode/toString、
 * 继承自 BaseEntity 的公共字段以及表名注解。
 */
class UserEntityTest {

    private static UserEntity sample() {
        UserEntity user = new UserEntity();
        user.setId("u1");
        user.setUsername("alice");
        user.setNickname("爱丽丝");
        user.setEmail("alice@cangjie.cn");
        user.setPhone("13800000000");
        user.setPassword("encoded");
        user.setRole("ADMIN");
        user.setIsActive(true);
        user.setSource("LOCAL");
        user.setLanguage("zh_CN");
        user.setAvatar("/avatar/a.png");
        return user;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        UserEntity user = sample();

        assertThat(user.getId()).isEqualTo("u1");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getNickname()).isEqualTo("爱丽丝");
        assertThat(user.getEmail()).isEqualTo("alice@cangjie.cn");
        assertThat(user.getPhone()).isEqualTo("13800000000");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getRole()).isEqualTo("ADMIN");
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getSource()).isEqualTo("LOCAL");
        assertThat(user.getLanguage()).isEqualTo("zh_CN");
        assertThat(user.getAvatar()).isEqualTo("/avatar/a.png");
    }

    @Test
    void defaultValuesShouldBeNull() {
        // 边界场景：新建对象所有字段默认为 null（含包装类型 isActive）
        UserEntity user = new UserEntity();

        assertThat(user.getId()).isNull();
        assertThat(user.getIsActive()).isNull();
        assertThat(user.getUsername()).isNull();
        assertThat(user.getCreateTime()).isNull();
        assertThat(user.getDeleted()).isNull();
    }

    @Test
    void baseEntityFieldsShouldBeWritable() {
        // 正常场景：审计字段来自 BaseEntity
        UserEntity user = new UserEntity();
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        user.setCreateBy("system");
        user.setUpdateBy("admin");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleted(0);

        assertThat(user.getCreateBy()).isEqualTo("system");
        assertThat(user.getUpdateBy()).isEqualTo("admin");
        assertThat(user.getCreateTime()).isEqualTo(now);
        assertThat(user.getUpdateTime()).isEqualTo(now);
        assertThat(user.getDeleted()).isZero();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllFieldsIncludingSuper() {
        // 正常场景：字段一致则相等；任一字段不同则不等（callSuper=true）
        UserEntity one = sample();
        UserEntity another = sample();
        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
        assertThat(one).isNotEqualTo(null);
        assertThat(one).isNotEqualTo("notAnUser");

        another.setRole("USER");
        assertThat(one).isNotEqualTo(another);

        UserEntity otherId = sample();
        otherId.setId("u2");
        assertThat(one).isNotEqualTo(otherId);
    }

    @Test
    void toStringShouldContainKeyFields() {
        // 正常场景：日志输出包含关键字段
        assertThat(sample().toString())
                .contains("username=alice")
                .contains("role=ADMIN");
    }

    @Test
    void tableNameShouldBeUser() {
        // 正常场景：表名注解映射到 user 表
        TableName tableName = UserEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("user");
        assertThat(tableName.autoResultMap()).isTrue();
    }
}
