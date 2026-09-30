package cn.cangjiecloud.prompt.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.RuleEntity;
import cn.cangjiecloud.prompt.mapper.RuleMapper;
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
 * {@link RuleServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code save/updateById/removeById/getById/page} 等 MyBatis-Plus 继承方法，不连接数据库。
 */
class RuleServiceImplTest {

    private RuleServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(RuleMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, RuleEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new RuleServiceImpl());
    }

    private static RuleEntity rule(String id, String name, String status, Integer priority) {
        RuleEntity entity = new RuleEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription("行为约束规则");
        entity.setCondition("regex:必须.*确认");
        entity.setAction("先确认再执行");
        entity.setStatus(status);
        entity.setPriority(priority);
        return entity;
    }

    @Nested
    @DisplayName("create：创建规则")
    class Create {

        @Test
        void 状态与优先级为空时填充默认值并保存() {
            doReturn(true).when(service).save(any(RuleEntity.class));

            RuleEntity created = service.create(rule("r1", "确认规则", "  ", null));

            ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
            verify(service).save(captor.capture());
            assertThat(created).isSameAs(captor.getValue());
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getPriority()).isZero();
        }

        @Test
        void 已指定状态与优先级时保持原值() {
            doReturn(true).when(service).save(any(RuleEntity.class));

            RuleEntity created = service.create(rule("r2", "停用规则", "inactive", 5));

            assertThat(created.getStatus()).isEqualTo("inactive");
            assertThat(created.getPriority()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("update：更新规则")
    class Update {

        @Test
        void 规则不存在时抛出业务异常() {
            doReturn(null).when(service).getById(any(Serializable.class));

            assertThatThrownBy(() -> service.update("r404", rule(null, "x", "active", 0)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("规则不存在");
            verify(service, never()).updateById(any(RuleEntity.class));
        }

        @Test
        void 存在时回填主键并返回最新数据() {
            RuleEntity existing = rule("r1", "旧规则", "active", 1);
            RuleEntity patch = rule(null, "新规则", "inactive", 9);
            doReturn(existing).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).updateById(any(RuleEntity.class));

            RuleEntity updated = service.update("r1", patch);

            assertThat(patch.getId()).isEqualTo("r1");
            verify(service).updateById(patch);
            assertThat(updated).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("delete：删除规则")
    class Delete {

        @Test
        void 规则不存在时静默返回() {
            doReturn(null).when(service).getById(any(Serializable.class));

            service.delete("r404");

            verify(service, never()).removeById(any(Serializable.class));
        }

        @Test
        void 存在时按主键删除() {
            doReturn(rule("r1", "待删", "active", 0)).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).removeById(any(Serializable.class));

            service.delete("r1");

            verify(service).removeById("r1");
        }
    }

    @Nested
    @DisplayName("pageQuery：分页查询")
    class PageQuery {

        @Test
        void 关键字同时模糊匹配名称与描述并按下发分页参数查询() {
            IPage<RuleEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<RuleEntity> result = service.pageQuery("确认", 3, 15);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<RuleEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<RuleEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());

            Page<?> requested = (Page<?>) pageCaptor.getValue();
            assertThat(requested.getCurrent()).isEqualTo(3);
            assertThat(requested.getSize()).isEqualTo(15);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql())
                    .contains("name LIKE")
                    .contains("description LIKE");
            assertThat(wrapper.getSqlSegment())
                    .containsIgnoringCase("ORDER BY")
                    .containsIgnoringCase("priority DESC")
                    .containsIgnoringCase("create_time DESC");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("%确认%", "%确认%");
        }

        @Test
        void 分页参数为空时使用默认值且关键字为空时不过滤() {
            IPage<RuleEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(null, null, null);

            ArgumentCaptor<IPage<RuleEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<RuleEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
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
