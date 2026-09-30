package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.entity.OperationLogEntity;
import cn.cangjiecloud.observability.mapper.OperationLogMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link OperationLogServiceImpl} 单元测试。
 * <p>
 * 覆盖操作日志落库的空值保护与分页查询的筛选条件、默认分页参数；
 * 通过 spy 拦截继承的 save/page 方法，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    private OperationLogServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(OperationLogMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, OperationLogEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new OperationLogServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", operationLogMapper);
    }

    @Nested
    class Record {

        @Test
        void shouldSkipNullEntity() {
            service.record(null);

            verify(service, never()).save(any(OperationLogEntity.class));
        }

        @Test
        void shouldSaveEntity() {
            doReturn(true).when(service).save(any(OperationLogEntity.class));
            OperationLogEntity entity = new OperationLogEntity();
            entity.setModule("chat");
            entity.setAction("send");
            entity.setStatus("success");

            service.record(entity);

            verify(service).save(entity);
        }
    }

    @Nested
    class PageQuery {

        @Test
        void shouldApplyFiltersAndOrderByCreateTimeDesc() {
            doReturn(new Page<>(2, 30)).when(service).page(any(IPage.class), any(Wrapper.class));

            IPage<OperationLogEntity> page = service.pageQuery("chat", "send", "fail", 2, 30);

            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(30);

            ArgumentCaptor<IPage<OperationLogEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<OperationLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            LambdaQueryWrapper<OperationLogEntity> wrapper =
                    (LambdaQueryWrapper<OperationLogEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("module", "action", "status");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("create_time");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("chat", "send", "fail");
        }

        @Test
        void shouldUseDefaultsAndSkipBlankFilters() {
            doReturn(new Page<>(1, 10)).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(" ", "", null, null, null);

            ArgumentCaptor<IPage<OperationLogEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<OperationLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
            LambdaQueryWrapper<OperationLogEntity> wrapper =
                    (LambdaQueryWrapper<OperationLogEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("module", "action", "status");
            assertThat(wrapper.getParamNameValuePairs()).isEmpty();
        }
    }

    @Test
    void shouldPropagateSaveFailureBecauseSyncWriteIsCallerVisible() {
        // record 为同步写入，异常直接抛给调用方（AOP 切面决定是否吞掉）
        doThrow(new RuntimeException("db down")).when(service).save(any(OperationLogEntity.class));

        assertThatThrownBy(() -> service.record(new OperationLogEntity()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }
}
