package cn.cangjiecloud.prompt.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.CommandEntity;
import cn.cangjiecloud.prompt.mapper.CommandMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link CommandServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code save/updateById/removeById/getById/page} 等 MyBatis-Plus 继承方法，不连接数据库。
 */
class CommandServiceImplTest {

    private CommandServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(CommandMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, CommandEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new CommandServiceImpl());
    }

    private static CommandEntity command(String id, String name, String status) {
        CommandEntity entity = new CommandEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCommand("/deploy");
        entity.setDescription("部署命令");
        entity.setStatus(status);
        return entity;
    }

    @Nested
    @DisplayName("create：创建命令")
    class Create {

        @Test
        void 状态为空时填充默认激活状态并保存() {
            doReturn(true).when(service).save(any(CommandEntity.class));

            CommandEntity created = service.create(command("c1", "部署", "  "));

            ArgumentCaptor<CommandEntity> captor = ArgumentCaptor.forClass(CommandEntity.class);
            verify(service).save(captor.capture());
            assertThat(created).isSameAs(captor.getValue());
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getName()).isEqualTo("部署");
        }

        @Test
        void 已指定状态时保持原值() {
            doReturn(true).when(service).save(any(CommandEntity.class));

            CommandEntity created = service.create(command("c2", "停用命令", "inactive"));

            assertThat(created.getStatus()).isEqualTo("inactive");
        }
    }

    @Nested
    @DisplayName("update：更新命令")
    class Update {

        @Test
        void 命令不存在时抛出业务异常() {
            doReturn(null).when(service).getById(any(Serializable.class));

            assertThatThrownBy(() -> service.update("c404", command(null, "x", "active")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("命令不存在");
            verify(service, never()).updateById(any(CommandEntity.class));
        }

        @Test
        void 存在时回填主键并返回最新数据() {
            CommandEntity existing = command("c1", "旧命令", "active");
            CommandEntity patch = command(null, "新命令", "inactive");
            doReturn(existing).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).updateById(any(CommandEntity.class));

            CommandEntity updated = service.update("c1", patch);

            assertThat(patch.getId()).isEqualTo("c1");
            verify(service).updateById(patch);
            assertThat(updated).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("delete：删除命令")
    class Delete {

        @Test
        void 命令不存在时静默返回() {
            doReturn(null).when(service).getById(any(Serializable.class));

            service.delete("c404");

            verify(service, never()).removeById(any(Serializable.class));
        }

        @Test
        void 存在时按主键删除() {
            doReturn(command("c1", "待删", "active")).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).removeById(any(Serializable.class));

            service.delete("c1");

            verify(service).removeById("c1");
        }
    }

    @Nested
    @DisplayName("pageQuery：分页查询")
    class PageQuery {

        @Test
        void 关键字同时模糊匹配名称命令与描述并按下发分页参数查询() {
            IPage<CommandEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<CommandEntity> result = service.pageQuery("deploy", 2, 20);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<CommandEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<CommandEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());

            Page<?> requested = (Page<?>) pageCaptor.getValue();
            assertThat(requested.getCurrent()).isEqualTo(2);
            assertThat(requested.getSize()).isEqualTo(20);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql())
                    .contains("name LIKE")
                    .contains("command LIKE")
                    .contains("description LIKE");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("%deploy%", "%deploy%", "%deploy%");
        }

        @Test
        void 分页参数为空时使用默认值且关键字为空时不过滤() {
            IPage<CommandEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(null, null, null);

            ArgumentCaptor<IPage<CommandEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<CommandEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());

            Page<?> requested = (Page<?>) pageCaptor.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("LIKE");
            assertThat(wrapper.getParamNameValuePairs()).isEmpty();
        }
    }
}
