package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.LlmTraceQueryDTO;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.mapper.LlmTraceMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link LlmTraceServiceImpl} 单元测试。
 * <p>
 * 覆盖列表筛选、时间线、按 run / trace 的精简列查询与单条摘要，校验 Wrapper 的列裁剪
 * （正文列必须被排除）与分页默认值；通过 spy 拦截继承的数据库方法，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class LlmTraceServiceImplTest {

    @Mock
    private LlmTraceMapper llmTraceMapper;

    private LlmTraceServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(LlmTraceMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, LlmTraceEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new LlmTraceServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", llmTraceMapper);
    }

    // ==================== pageQuery ====================

    @Nested
    class PageQuery {

        @Test
        void shouldApplyAllFiltersAndOrderByStartTimeDesc() {
            doReturn(new Page<>(2, 5)).when(service).page(any(IPage.class), any(Wrapper.class));
            LlmTraceQueryDTO query = new LlmTraceQueryDTO();
            query.setTraceId("trace-1");
            query.setRequestId("req-1");
            query.setAppId("app-1");
            query.setAppName("demo");
            query.setSessionId("session-1");
            query.setUserId("user-1");
            query.setModelId("model-1");
            query.setModelName("gpt");
            query.setStatus("success");
            query.setPromptKeyword("hello");
            query.setResponseKeyword("world");
            query.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
            query.setEndTime(LocalDateTime.of(2026, 2, 1, 0, 0));
            query.setPageNum(2);
            query.setPageSize(5);

            IPage<LlmTraceEntity> page = service.pageQuery(query);

            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(5);

            ArgumentCaptor<IPage<LlmTraceEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<LlmTraceEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            LambdaQueryWrapper<LlmTraceEntity> wrapper =
                    (LambdaQueryWrapper<LlmTraceEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).contains("trace_id", "request_id", "app_id", "app_name",
                    "session_id", "user_id", "model_id", "model_name", "status", "prompt_content",
                    "response_content", "start_time");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("%trace-1%", "%req-1%", "app-1", "%demo%", "session-1", "user-1",
                            "model-1", "%gpt%", "success");
        }

        @Test
        void shouldUsePagingDefaultsAndSkipBlankFilters() {
            doReturn(new Page<>(1, 10)).when(service).page(any(IPage.class), any(Wrapper.class));
            LlmTraceQueryDTO query = new LlmTraceQueryDTO();
            query.setPageNum(null);
            query.setPageSize(null);

            service.pageQuery(query);

            ArgumentCaptor<IPage<LlmTraceEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<LlmTraceEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCaptor.capture(), wrapperCaptor.capture());
            assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
            assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
            LambdaQueryWrapper<LlmTraceEntity> wrapper =
                    (LambdaQueryWrapper<LlmTraceEntity>) wrapperCaptor.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("trace_id", "session_id", "status");
            assertThat(wrapper.getParamNameValuePairs()).isEmpty();
        }
    }

    // ==================== 单条查询 ====================

    @Nested
    class DetailAndTimeline {

        @Test
        void shouldDelegateGetDetailToGetById() {
            LlmTraceEntity entity = new LlmTraceEntity();
            entity.setId("llm-1");
            doReturn(entity).when(service).getById("llm-1");

            assertThat(service.getDetail("llm-1")).isSameAs(entity);
        }
    }

    // ==================== 精简列查询 ====================

    @Nested
    class LightColumnQueries {

        @Test
        void shouldReturnEmptyListForBlankRunIdAndTraceId() {
            assertThat(service.listByRun(" ")).isEmpty();
            assertThat(service.listByRun(null)).isEmpty();
            assertThat(service.listBackgroundByTrace("")).isEmpty();
            assertThat(service.listBackgroundByTrace(null)).isEmpty();

            verify(service, never()).list(any(Wrapper.class));
        }

        @Test
        void shouldQueryRunTracesWithoutContentColumns() {
            LlmTraceEntity entity = new LlmTraceEntity();
            doReturn(List.of(entity)).when(service).list(any(Wrapper.class));

            assertThat(service.listByRun("run-1")).containsExactly(entity);

            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            LambdaQueryWrapper<LlmTraceEntity> wrapper = (LambdaQueryWrapper<LlmTraceEntity>) captor.getValue();
            assertThat(wrapper.getSqlSelect())
                    .contains("id", "run_id", "trace_id", "total_tokens", "status")
                    .doesNotContain("prompt_content", "response_content");
            assertThat(wrapper.getTargetSql()).contains("run_id");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");
        }

        @Test
        void shouldQueryBackgroundTracesWithNullRunIdOnly() {
            LlmTraceEntity entity = new LlmTraceEntity();
            doReturn(List.of(entity)).when(service).list(any(Wrapper.class));

            assertThat(service.listBackgroundByTrace("trace-1")).containsExactly(entity);

            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            LambdaQueryWrapper<LlmTraceEntity> wrapper = (LambdaQueryWrapper<LlmTraceEntity>) captor.getValue();
            assertThat(wrapper.getSqlSelect()).doesNotContain("prompt_content", "response_content");
            assertThat(wrapper.getTargetSql()).contains("trace_id", "run_id");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("IS NULL").containsIgnoringCase("ORDER BY");
        }

        @Test
        void shouldReturnNullBriefForBlankId() {
            assertThat(service.getBrief(null)).isNull();
            assertThat(service.getBrief(" ")).isNull();

            verify(service, never()).getOne(any(Wrapper.class));
        }

        @Test
        void shouldQueryBriefWithLimitOneAndLightColumns() {
            LlmTraceEntity entity = new LlmTraceEntity();
            doReturn(entity).when(service).getOne(any(Wrapper.class));

            assertThat(service.getBrief("llm-1")).isSameAs(entity);

            ArgumentCaptor<Wrapper<LlmTraceEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture());
            LambdaQueryWrapper<LlmTraceEntity> wrapper = (LambdaQueryWrapper<LlmTraceEntity>) captor.getValue();
            assertThat(wrapper.getSqlSelect()).doesNotContain("prompt_content", "response_content");
            assertThat(wrapper.getTargetSql()).contains("id");
            assertThat(wrapper.getSqlSegment()).contains("LIMIT 1");
            assertThat(wrapper.getParamNameValuePairs().values()).contains("llm-1");
        }
    }
}
