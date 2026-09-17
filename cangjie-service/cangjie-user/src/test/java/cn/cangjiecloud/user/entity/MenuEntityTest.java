package cn.cangjiecloud.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MenuEntity} 单元测试：访问器、树形 children 字段以及
 * {@code @TableField(exist = false)} 非数据库字段声明。
 */
class MenuEntityTest {

    private static MenuEntity sample() {
        MenuEntity menu = new MenuEntity();
        menu.setId("m1");
        menu.setName("用户管理");
        menu.setCode("system:user");
        menu.setPath("/system/user");
        menu.setComponent("system/user/index");
        menu.setIcon("user");
        menu.setParentId("0");
        menu.setSort(1);
        menu.setType("menu");
        menu.setStatus("active");
        return menu;
    }

    @Test
    void gettersShouldReturnAssignedValues() {
        // 正常场景：全字段读写
        MenuEntity menu = sample();

        assertThat(menu.getId()).isEqualTo("m1");
        assertThat(menu.getName()).isEqualTo("用户管理");
        assertThat(menu.getCode()).isEqualTo("system:user");
        assertThat(menu.getPath()).isEqualTo("/system/user");
        assertThat(menu.getComponent()).isEqualTo("system/user/index");
        assertThat(menu.getIcon()).isEqualTo("user");
        assertThat(menu.getParentId()).isEqualTo("0");
        assertThat(menu.getSort()).isEqualTo(1);
        assertThat(menu.getType()).isEqualTo("menu");
        assertThat(menu.getStatus()).isEqualTo("active");
    }

    @Test
    void childrenShouldBeAssignableForTreeStructure() {
        // 正常场景：children 用于承载子菜单（树形结构）
        MenuEntity root = sample();
        MenuEntity child = sample();
        child.setId("m2");
        root.setChildren(List.of(child));

        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().get(0).getId()).isEqualTo("m2");
    }

    @Test
    void childrenShouldBeNullByDefault() {
        // 边界场景：叶子节点未设置 children 时为 null，且 children 不参与 equals
        MenuEntity one = sample();
        MenuEntity another = sample();
        assertThat(one.getChildren()).isNull();
        assertThat(one).isEqualTo(another);

        one.setChildren(List.of(another));
        assertThat(one).isNotEqualTo(another);
    }

    @Test
    void childrenFieldShouldBeMarkedAsNonDatabaseColumn() {
        // 正常场景：children 是虚拟字段，不能出现在 SQL 列中
        Field field;
        try {
            field = MenuEntity.class.getDeclaredField("children");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("children 字段应存在", e);
        }
        TableField tableField = field.getAnnotation(TableField.class);
        assertThat(tableField).isNotNull();
        assertThat(tableField.exist()).isFalse();
    }

    @Test
    void equalsAndHashCodeShouldConsiderAllPersistentFields() {
        // 正常场景：任一持久化字段不同则不相等
        MenuEntity one = sample();
        MenuEntity another = sample();
        assertThat(one).hasSameHashCodeAs(another);

        another.setSort(null);
        assertThat(one.getSort()).isNotEqualTo(another.getSort());
        assertThat(one).isNotEqualTo(another);
    }

    @Test
    void toStringShouldContainName() {
        // 正常场景：toString 输出包含菜单名称
        assertThat(sample().toString()).contains("name=用户管理");
    }

    @Test
    void tableNameShouldBeMenu() {
        // 正常场景：表名注解映射到 menu 表
        TableName tableName = MenuEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("menu");
    }
}
