package cn.cangjiecloud.prompt.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.SkillEntity;
import cn.cangjiecloud.prompt.mapper.SkillMapper;
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
 * {@link SkillServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 {@code save/updateById/removeById/getById/page} 等 MyBatis-Plus 继承方法，不连接数据库。
 */
class SkillServiceImplTest {

    private SkillServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // 让 LambdaQueryWrapper 能解析 SFunction -> 列名，无需真实数据库
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(SkillMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SkillEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new SkillServiceImpl());
    }

    private static SkillEntity skill(String id, String name, String status) {
        SkillEntity entity = new SkillEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription("天气查询技能");
        entity.setType("function");
        entity.setContent("查询指定城市天气");
        entity.setStatus(status);
        return entity;
    }

    @Nested
    @DisplayName("create：创建技能")
    class Create {

        @Test
        void 状态为空时填充默认激活状态并保存() {
            doReturn(true).when(service).save(any(SkillEntity.class));

            SkillEntity created = service.create(skill("s1", "天气", "\t"));

            ArgumentCaptor<SkillEntity> captor = ArgumentCaptor.forClass(SkillEntity.class);
            verify(service).save(captor.capture());
            assertThat(created).isSameAs(captor.getValue());
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getName()).isEqualTo("天气");
        }

        @Test
        void 已指定状态时保持原值() {
            doReturn(true).when(service).save(any(SkillEntity.class));

            SkillEntity created = service.create(skill("s2", "停用技能", "inactive"));

            assertThat(created.getStatus()).isEqualTo("inactive");
        }
    }

    @Nested
    @DisplayName("update：更新技能")
    class Update {

        @Test
        void 技能不存在时抛出业务异常() {
            doReturn(null).when(service).getById(any(Serializable.class));

            assertThatThrownBy(() -> service.update("s404", skill(null, "x", "active")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("技能不存在");
            verify(service, never()).updateById(any(SkillEntity.class));
        }

        @Test
        void 存在时回填主键并返回最新数据() {
            SkillEntity existing = skill("s1", "旧技能", "active");
            SkillEntity patch = skill(null, "新技能", "inactive");
            doReturn(existing).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).updateById(any(SkillEntity.class));

            SkillEntity updated = service.update("s1", patch);

            assertThat(patch.getId()).isEqualTo("s1");
            verify(service).updateById(patch);
            assertThat(updated).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("delete：删除技能")
    class Delete {

        @Test
        void 技能不存在时静默返回() {
            doReturn(null).when(service).getById(any(Serializable.class));

            service.delete("s404");

            verify(service, never()).removeById(any(Serializable.class));
        }

        @Test
        void 存在时按主键删除() {
            doReturn(skill("s1", "待删", "active")).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).removeById(any(Serializable.class));

            service.delete("s1");

            verify(service).removeById("s1");
        }
    }

    @Nested
    @DisplayName("pageQuery：分页查询")
    class PageQuery {

        @Test
        void 关键字同时模糊匹配名称与描述并按创建时间倒序查询() {
            IPage<SkillEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<SkillEntity> result = service.pageQuery("天气", 2, 5);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<SkillEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<SkillEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());

            Page<?> requested = (Page<?>) pageCaptor.getValue();
            assertThat(requested.getCurrent()).isEqualTo(2);
            assertThat(requested.getSize()).isEqualTo(5);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql())
                    .contains("name LIKE")
                    .contains("description LIKE");
            assertThat(wrapper.getSqlSegment())
                    .containsIgnoringCase("ORDER BY create_time DESC");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("%天气%", "%天气%");
        }

        @Test
        void 分页参数为空时使用默认值且关键字为空时不过滤() {
            IPage<SkillEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(null, null, null);

            ArgumentCaptor<IPage<SkillEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<SkillEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
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
