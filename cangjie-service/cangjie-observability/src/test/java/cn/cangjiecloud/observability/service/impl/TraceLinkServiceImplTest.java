package cn.cangjiecloud.observability.service.impl;

import cn.cangjiecloud.observability.dto.AgentRunDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkDetailVO;
import cn.cangjiecloud.observability.dto.TraceLinkQueryDTO;
import cn.cangjiecloud.observability.dto.TraceLinkVO;
import cn.cangjiecloud.observability.entity.AgentRunEntity;
import cn.cangjiecloud.observability.entity.LlmTraceEntity;
import cn.cangjiecloud.observability.entity.TraceRecordEntity;
import cn.cangjiecloud.observability.mapper.AgentRunMapper;
import cn.cangjiecloud.observability.mapper.TraceLinkMapper;
import cn.cangjiecloud.observability.mapper.TraceRecordMapper;
import cn.cangjiecloud.observability.service.IAgentRunService;
import cn.cangjiecloud.observability.service.ILlmTraceService;
import cn.cangjiecloud.observability.service.ITraceRecordService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceLinkServiceImpl} 单元测试。
 * <p>
 * 覆盖聚合分页的默认回溯窗口/手工分页参数、孤立链路 {@code #kind#id} 键的解析分发，
 * 以及普通链路的三段明细装配（span / run / 后台模型调用），不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class TraceLinkServiceImplTest {

    @Mock
    private TraceLinkMapper traceLinkMapper;

    @Mock
    private ITraceRecordService traceRecordService;

    @Mock
    private IAgentRunService agentRunService;

    @Mock
    private ILlmTraceService llmTraceService;

    private TraceLinkServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        initTableInfo(TraceRecordMapper.class, TraceRecordEntity.class);
        initTableInfo(AgentRunMapper.class, AgentRunEntity.class);
    }

    private static void initTableInfo(Class<?> mapper, Class<?> entity) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new TraceLinkServiceImpl(traceLinkMapper, traceRecordService, agentRunService, llmTraceService);
    }

    private TraceLinkVO summary(String traceId) {
        TraceLinkVO vo = new TraceLinkVO();
        vo.setTraceId(traceId);
        return vo;
    }

    // ==================== pageQuery ====================

    @Nested
    class PageQuery {

        @Test
        void shouldApplyDefaultWindowWhenStartTimeMissing() {
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            query.setLinkId("force-null");
            query.setOrphanId("force-null");
            query.setEndTime(LocalDateTime.of(2026, 5, 20, 12, 0));
            AtomicReference<TraceLinkQueryDTO> captured = new AtomicReference<>();
            when(traceLinkMapper.countTraceLinks(any())).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return 0L;
            });

            IPage<TraceLinkVO> page = service.pageQuery(query);

            TraceLinkQueryDTO probed = captured.get();
            assertThat(probed.getLinkId()).isNull();
            assertThat(probed.getOrphanId()).isNull();
            assertThat(probed.getStartTime()).isEqualTo(LocalDateTime.of(2026, 5, 13, 12, 0));
            assertThat(probed.getEndTime()).isEqualTo(LocalDateTime.of(2026, 5, 20, 12, 0));
            assertThat(page.getRecords()).isEmpty();
            assertThat(page.getTotal()).isZero();
            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(20);
            assertThat(page.searchCount()).isFalse();
            verify(traceLinkMapper, never()).selectTraceLinks(any(), anyLong(), anyLong());
        }

        @Test
        void shouldUseNowAsEndTimeWhenBothBoundsMissing() {
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            AtomicReference<TraceLinkQueryDTO> captured = new AtomicReference<>();
            when(traceLinkMapper.countTraceLinks(any())).thenAnswer(invocation -> {
                captured.set(invocation.getArgument(0));
                return 0L;
            });
            LocalDateTime before = LocalDateTime.now();

            service.pageQuery(query);

            LocalDateTime end = captured.get().getEndTime();
            assertThat(end).isBetween(before, LocalDateTime.now());
            assertThat(captured.get().getStartTime()).isEqualTo(end.minusDays(7));
        }

        @Test
        void shouldKeepExplicitStartTimeAndQueryWithOffset() {
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            query.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
            query.setPageNum(3);
            query.setPageSize(15);
            when(traceLinkMapper.countTraceLinks(any())).thenReturn(42L);
            TraceLinkVO first = summary("trace-1");
            TraceLinkVO second = summary("trace-2");
            when(traceLinkMapper.selectTraceLinks(any(), anyLong(), anyLong()))
                    .thenReturn(List.of(first, second));

            IPage<TraceLinkVO> page = service.pageQuery(query);

            assertThat(page.getRecords()).containsExactly(first, second);
            assertThat(page.getTotal()).isEqualTo(42L);
            assertThat(page.getCurrent()).isEqualTo(3);
            assertThat(page.getSize()).isEqualTo(15);

            ArgumentCaptor<Long> limitCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);
            verify(traceLinkMapper).selectTraceLinks(any(), limitCaptor.capture(), offsetCaptor.capture());
            assertThat(limitCaptor.getValue()).isEqualTo(15L);
            assertThat(offsetCaptor.getValue()).isEqualTo(30L);
        }

        @Test
        void shouldFallbackToFirstPageForInvalidPaging() {
            TraceLinkQueryDTO query = new TraceLinkQueryDTO();
            query.setStartTime(LocalDateTime.now().minusDays(1));
            query.setPageNum(0);
            query.setPageSize(-5);
            when(traceLinkMapper.countTraceLinks(any())).thenReturn(1L);
            when(traceLinkMapper.selectTraceLinks(any(), anyLong(), anyLong()))
                    .thenReturn(List.of(summary("trace-1")));

            IPage<TraceLinkVO> page = service.pageQuery(query);

            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(20);
            ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);
            verify(traceLinkMapper).selectTraceLinks(any(), anyLong(), offsetCaptor.capture());
            assertThat(offsetCaptor.getValue()).isZero();
        }
    }

    // ==================== detail ====================

    @Nested
    class Detail {

        @Test
        void shouldReturnNullOnBlankOrMalformedLinkId() {
            assertThat(service.detail(null)).isNull();
            assertThat(service.detail(" ")).isNull();
            assertThat(service.detail("#span")).isNull();
            assertThat(service.detail("#span#")).isNull();
            assertThat(service.detail("#span#  ")).isNull();

            verify(traceLinkMapper, never()).selectTraceLink(any());
        }

        @Test
        void shouldReturnNullWhenSummaryMissing() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(null);

            assertThat(service.detail("trace-1")).isNull();
            verify(traceRecordService, never()).list(any(Wrapper.class));
            verify(agentRunService, never()).list(any(Wrapper.class));
            verify(llmTraceService, never()).listBackgroundByTrace(any());
        }

        @Test
        void shouldReturnEmptyDetailWhenNonOrphanChainHasNothing() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("trace-1"));
            when(traceRecordService.list(any(Wrapper.class))).thenReturn(List.of());
            when(agentRunService.list(any(Wrapper.class))).thenReturn(List.of());
            when(llmTraceService.listBackgroundByTrace("trace-1")).thenReturn(List.of());

            TraceLinkDetailVO detail = service.detail("trace-1");

            assertThat(detail).isNotNull();
            assertThat(detail.getSummary().getTraceId()).isEqualTo("trace-1");
            assertThat(detail.getSpans()).isEmpty();
            assertThat(detail.getRuns()).isEmpty();
            assertThat(detail.getBackgroundLlmTraces()).isEmpty();

            ArgumentCaptor<TraceLinkQueryDTO> probeCaptor = ArgumentCaptor.forClass(TraceLinkQueryDTO.class);
            verify(traceLinkMapper).selectTraceLink(probeCaptor.capture());
            assertThat(probeCaptor.getValue().getLinkId()).isEqualTo("trace-1");
            assertThat(probeCaptor.getValue().getOrphanId()).isNull();
        }

        @Test
        void shouldAssembleSpansRunsAndBackgroundTracesForNonOrphanChain() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("trace-1"));
            TraceRecordEntity span = new TraceRecordEntity();
            span.setSpanId("span-1");
            when(traceRecordService.list(any(Wrapper.class))).thenReturn(List.of(span));
            AgentRunEntity run = new AgentRunEntity();
            run.setId("run-1");
            when(agentRunService.list(any(Wrapper.class))).thenReturn(List.of(run));
            AgentRunDetailVO runDetail = new AgentRunDetailVO();
            runDetail.setRun(run);
            runDetail.setChildren(List.of(new AgentRunEntity()));
            when(agentRunService.detail("run-1")).thenReturn(runDetail);
            LlmTraceEntity background = new LlmTraceEntity();
            background.setId("llm-bg");
            when(llmTraceService.listBackgroundByTrace("trace-1"))
                    .thenReturn(List.of(background));

            TraceLinkDetailVO detail = service.detail("trace-1");

            assertThat(detail.getSpans()).containsExactly(span);
            assertThat(detail.getRuns()).containsExactly(runDetail);
            assertThat(runDetail.getChildren()).isNull();
            assertThat(detail.getBackgroundLlmTraces()).containsExactly(background);

            ArgumentCaptor<Wrapper<TraceRecordEntity>> spanCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(traceRecordService).list(spanCaptor.capture());
            LambdaQueryWrapper<TraceRecordEntity> spanWrapper =
                    (LambdaQueryWrapper<TraceRecordEntity>) spanCaptor.getValue();
            assertThat(spanWrapper.getTargetSql()).contains("trace_id");
            assertThat(spanWrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");

            ArgumentCaptor<Wrapper<AgentRunEntity>> runCaptor = ArgumentCaptor.forClass(Wrapper.class);
            verify(agentRunService, times(1)).list(runCaptor.capture());
            LambdaQueryWrapper<AgentRunEntity> runWrapper = (LambdaQueryWrapper<AgentRunEntity>) runCaptor.getValue();
            assertThat(runWrapper.getTargetSql()).contains("trace_id");
            assertThat(runWrapper.getSqlSegment()).containsIgnoringCase("ORDER BY").contains("start_time");
        }

        @Test
        void shouldSkipRunWhoseDetailMissing() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("trace-1"));
            AgentRunEntity run = new AgentRunEntity();
            run.setId("run-1");
            when(traceRecordService.list(any(Wrapper.class))).thenReturn(List.of());
            when(agentRunService.list(any(Wrapper.class))).thenReturn(List.of(run));
            when(agentRunService.detail("run-1")).thenReturn(null);

            TraceLinkDetailVO detail = service.detail("trace-1");

            assertThat(detail.getRuns()).isEmpty();
        }

        @Test
        void shouldResolveOrphanSpanChain() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#span#rec-1"));
            TraceRecordEntity span = new TraceRecordEntity();
            span.setSpanId("rec-1");
            when(traceRecordService.getById("rec-1")).thenReturn(span);

            TraceLinkDetailVO detail = service.detail("#span#rec-1");

            assertThat(detail.getSpans()).containsExactly(span);
            assertThat(detail.getRuns()).isEmpty();
            assertThat(detail.getBackgroundLlmTraces()).isEmpty();

            ArgumentCaptor<TraceLinkQueryDTO> probeCaptor = ArgumentCaptor.forClass(TraceLinkQueryDTO.class);
            verify(traceLinkMapper).selectTraceLink(probeCaptor.capture());
            assertThat(probeCaptor.getValue().getOrphanId()).isEqualTo("rec-1");
            verifyNoBackgroundQueries();
        }

        @Test
        void shouldResolveOrphanRunChainWithChildrenStripped() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#run#run-9"));
            AgentRunDetailVO runDetail = new AgentRunDetailVO();
            AgentRunEntity run = new AgentRunEntity();
            run.setId("run-9");
            runDetail.setRun(run);
            runDetail.setChildren(List.of(new AgentRunEntity()));
            when(agentRunService.detail("run-9")).thenReturn(runDetail);

            TraceLinkDetailVO detail = service.detail("#run#run-9");

            assertThat(detail.getRuns()).containsExactly(runDetail);
            assertThat(runDetail.getChildren()).isNull();
            assertThat(detail.getSpans()).isEmpty();
            verifyNoBackgroundQueries();
        }

        @Test
        void shouldResolveOrphanLlmChainAndIgnoreUnknownKind() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#llm#llm-1"));
            LlmTraceEntity trace = new LlmTraceEntity();
            trace.setId("llm-1");
            when(llmTraceService.getBrief("llm-1")).thenReturn(trace);

            TraceLinkDetailVO detail = service.detail("#llm#llm-1");
            assertThat(detail.getBackgroundLlmTraces()).containsExactly(trace);

            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#unknown#x"));
            TraceLinkDetailVO unknown = service.detail("#unknown#x");
            assertThat(unknown.getSpans()).isEmpty();
            assertThat(unknown.getRuns()).isEmpty();
            assertThat(unknown.getBackgroundLlmTraces()).isEmpty();
        }

        @Test
        void shouldKeepOrphanLlmDetailEmptyWhenBriefMissing() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#llm#gone"));
            when(llmTraceService.getBrief("gone")).thenReturn(null);

            assertThat(service.detail("#llm#gone").getBackgroundLlmTraces()).isEmpty();
        }

        @Test
        void shouldKeepOrphanSpanDetailEmptyWhenRecordMissing() {
            when(traceLinkMapper.selectTraceLink(any())).thenReturn(summary("#span#gone"));
            when(traceRecordService.getById("gone")).thenReturn(null);

            assertThat(service.detail("#span#gone").getSpans()).isEmpty();
        }

        private void verifyNoBackgroundQueries() {
            verify(traceRecordService, never()).list(any(Wrapper.class));
            verify(agentRunService, never()).list(any(Wrapper.class));
            verify(llmTraceService, never()).listBackgroundByTrace(any());
        }
    }
}
