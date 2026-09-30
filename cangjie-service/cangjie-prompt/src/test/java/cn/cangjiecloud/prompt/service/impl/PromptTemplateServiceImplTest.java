package cn.cangjiecloud.prompt.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;
import cn.cangjiecloud.prompt.mapper.PromptTemplateMapper;
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
 * {@link PromptTemplateServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 MyBatis-Plus 继承方法，不连接数据库，重点验证默认值填充、不存在异常与查询条件。
 */
class PromptTemplateServiceImplTest {

    private PromptTemplateServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(PromptTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, PromptTemplateEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new PromptTemplateServiceImpl());
    }

    private static PromptTemplateEntity template(String id, String name, String status, Boolean isDefault) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setContent("你是${name}");
        entity.setDescription("客服模板");
        entity.setStatus(status);
        entity.setIsDefault(isDefault);
        return entity;
    }

    @Nested
    @DisplayName("create：创建模板")
    class Create {

        @Test
        void 状态与默认标记为空时填充默认值() {
            doReturn(true).when(service).save(any(PromptTemplateEntity.class));

            PromptTemplateEntity created = service.create(template("t1", "客服", "  ", null));

            ArgumentCaptor<PromptTemplateEntity> captor = ArgumentCaptor.forClass(PromptTemplateEntity.class);
            verify(service).save(captor.capture());
            assertThat(created).isSameAs(captor.getValue());
            assertThat(created.getStatus()).isEqualTo("active");
            assertThat(created.getIsDefault()).isFalse();
        }

        @Test
        void 已指定状态与默认标记时保持原值() {
            doReturn(true).when(service).save(any(PromptTemplateEntity.class));

            PromptTemplateEntity created = service.create(template("t2", "默认模板", "inactive", true));

            assertThat(created.getStatus()).isEqualTo("inactive");
            assertThat(created.getIsDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("update：更新模板")
    class Update {

        @Test
        void 模板不存在时抛出业务异常() {
            doReturn(null).when(service).getById(any(Serializable.class));

            assertThatThrownBy(() -> service.update("t404", template(null, "x", "active", false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("提示词模板不存在");
            verify(service, never()).updateById(any(PromptTemplateEntity.class));
        }

        @Test
        void 存在时回填主键并返回最新数据() {
            PromptTemplateEntity existing = template("t1", "旧模板", "active", false);
            PromptTemplateEntity patch = template(null, "新模板", "active", true);
            doReturn(existing).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).updateById(any(PromptTemplateEntity.class));

            PromptTemplateEntity updated = service.update("t1", patch);

            assertThat(patch.getId()).isEqualTo("t1");
            verify(service).updateById(patch);
            assertThat(updated).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("delete：删除模板")
    class Delete {

        @Test
        void 模板不存在时静默返回() {
            doReturn(null).when(service).getById(any(Serializable.class));

            service.delete("t404");

            verify(service, never()).removeById(any(Serializable.class));
        }

        @Test
        void 存在时按主键删除() {
            doReturn(template("t1", "待删", "active", false)).when(service).getById(any(Serializable.class));
            doReturn(true).when(service).removeById(any(Serializable.class));

            service.delete("t1");

            verify(service).removeById("t1");
        }
    }

    @Nested
    @DisplayName("pageQuery：分页查询")
    class PageQuery {

        @Test
        void 默认模板优先且按创建时间倒序并模糊匹配名称与描述() {
            IPage<PromptTemplateEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<PromptTemplateEntity> result = service.pageQuery("客服", 3, 5);

            assertThat(result).isSameAs(stub);
            ArgumentCaptor<IPage<PromptTemplateEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<PromptTemplateEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());

            Page<?> requested = (Page<?>) pageCaptor.getValue();
            assertThat(requested.getCurrent()).isEqualTo(3);
            assertThat(requested.getSize()).isEqualTo(5);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("name LIKE").contains("description LIKE");
            assertThat(wrapper.getSqlSegment())
                    .containsIgnoringCase("ORDER BY is_default DESC,create_time DESC");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("%客服%", "%客服%");
        }

        @Test
        void 分页参数为空时使用默认值且关键字为空时不过滤() {
            IPage<PromptTemplateEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery("", null, null);

            ArgumentCaptor<IPage<PromptTemplateEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<PromptTemplateEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
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
