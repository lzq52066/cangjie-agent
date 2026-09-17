package cn.cangjiecloud.user.controller;

import cn.cangjiecloud.common.api.R;
import cn.cangjiecloud.user.entity.UserEntity;
import cn.cangjiecloud.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserController} 单元测试：直接 new Controller 并 mock Mapper，
 * 通过捕获查询 Wrapper 断言 SQL 片段，验证下拉选项接口的查询条件与字段裁剪。
 */
class UserControllerTest {

    private UserMapper userMapper;
    private UserController controller;

    @BeforeAll
    static void installTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        // 预注册实体的 TableInfo，使 LambdaQueryWrapper 无需数据库即可解析出真实列名
        assistant.setCurrentNamespace("cn.cangjiecloud.user.mapper.UserMapper");
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        controller = new UserController(userMapper);
    }

    private static UserEntity user(String id, String username, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEmail(username + "@cangjie.cn");
        user.setPhone("13800000000");
        user.setPassword("should-not-be-exposed");
        return user;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<UserEntity> captureWrapper() {
        ArgumentCaptor<Wrapper<UserEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<UserEntity>) captor.getValue();
    }

    @Test
    void optionsShouldReturnTrimmedUserMaps() {
        // 正常场景：关键字命中，返回字段裁剪后的 Map 列表
        when(userMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(user("u1", "alice", "爱丽丝"), user("u2", "alicia", "艾丽西亚")));

        R<List<Map<String, Object>>> response = controller.options("ali");

        List<Map<String, Object>> data = response.getData();
        assertThat(data).hasSize(2);
        assertThat(data.get(0))
                .containsEntry("userId", "u1")
                .containsEntry("username", "alice")
                .containsEntry("nickname", "爱丽丝")
                .containsEntry("email", "alice@cangjie.cn")
                .containsEntry("phone", "13800000000")
                // 绝不能包含密码等敏感字段
                .doesNotContainKey("password")
                .hasSize(5);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void optionsShouldBuildKeywordQueryWithSelectedColumns() {
        // 正常场景：断言查询条件与查询列
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        controller.options("ali");

        LambdaQueryWrapper<UserEntity> wrapper = captureWrapper();
        String sql = wrapper.getTargetSql();
        assertThat(sql).contains("username LIKE").contains("OR").contains("nickname LIKE");
        assertThat(sql).containsIgnoringCase("ORDER BY username ASC");
        assertThat(wrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("%ali%", "%ali%");
        // 只查询下拉需要的列，不查密码
        assertThat(wrapper.getSqlSelect())
                .contains("id").contains("username").contains("nickname")
                .contains("email").contains("phone")
                .doesNotContain("password");
    }

    @Test
    void optionsShouldSkipKeywordConditionWhenBlank() {
        // 边界场景：关键字为空串/空白时不拼 LIKE 条件，仅排序
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        controller.options("   ");

        LambdaQueryWrapper<UserEntity> wrapper = captureWrapper();
        assertThat(wrapper.getTargetSql()).doesNotContain("LIKE");
        assertThat(wrapper.getTargetSql()).containsIgnoringCase("ORDER BY username ASC");
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    @Test
    void optionsShouldSkipKeywordConditionWhenNull() {
        // 边界场景：关键字为 null 时不拼 LIKE 条件
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        controller.options(null);

        assertThat(captureWrapper().getTargetSql()).doesNotContain("LIKE");
    }

    @Test
    void optionsShouldReturnEmptyListWhenNoUserMatched() {
        // 边界场景：无匹配用户返回空集合而非 null
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(controller.options("none").getData()).isEmpty();
    }
}
