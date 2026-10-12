package cn.cangjiecloud.eval.controller;

import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.dto.EvalCaseImportDTO;
import cn.cangjiecloud.eval.dto.EvalImportResultDTO;
import cn.cangjiecloud.eval.dto.EvalRunCompareDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalRequestDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalSummaryDTO;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalResultEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalResultService;
import cn.cangjiecloud.eval.service.IEvalRunService;
import cn.cangjiecloud.eval.service.IOnlineEvalService;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalController} 单元测试：覆盖各接口的委托、路径参数覆盖、分页包装、对话回流、
 * 运行对比与在线评测。
 */
@ExtendWith(MockitoExtension.class)
class EvalControllerTest {

    @Mock
    private IEvalDatasetService datasetService;
    @Mock
    private IEvalCaseService caseService;
    @Mock
    private IEvalRunService runService;
    @Mock
    private IEvalResultService resultService;
    @Mock
    private IOnlineEvalService onlineEvalService;

    private EvalController controller;

    @BeforeAll
    static void installTableInfoCache() {
        // listRuns 内部构造 LambdaQueryWrapper，需要 TableInfo 缓存解析列名
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.eval.mapper.EvalRunMapper");
        TableInfoHelper.initTableInfo(assistant, EvalRunEntity.class);
    }

    @BeforeEach
    void setUp() {
        controller = new EvalController(datasetService, caseService, runService, resultService,
                onlineEvalService);
    }

    private static <T> IPage<T> pageOf(T record, int current, int size) {
        Page<T> page = new Page<>(current, size);
        page.setRecords(List.of(record));
        page.setTotal(1);
        return page;
    }

    @Nested
    class DatasetEndpoints {

        /** 列表接口把 IPage 包装为 PageResult 并透传查询参数 */
        @Test
        void listDatasetsShouldWrapPageResult() {
            EvalDatasetEntity dataset = new EvalDatasetEntity();
            dataset.setId("d1");
            dataset.setName("回归集");
            when(datasetService.pageQuery("回归", 2, 20)).thenReturn(pageOf(dataset, 2, 20));

            PageResult<EvalDatasetEntity> result = controller.listDatasets("回归", 2, 20);

            assertThat(result.getList()).containsExactly(dataset);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(20);
        }

        /** 新增数据集直接委托服务层 */
        @Test
        void createDatasetShouldDelegate() {
            EvalDatasetEntity payload = new EvalDatasetEntity();
            payload.setName("新集");
            when(datasetService.create(payload)).thenReturn(payload);

            assertThat(controller.createDataset(payload)).isSameAs(payload);
            verify(datasetService).create(payload);
        }

        /** 更新数据集需透传路径 ID */
        @Test
        void updateDatasetShouldPassPathId() {
            EvalDatasetEntity payload = new EvalDatasetEntity();
            payload.setName("改名");
            when(datasetService.update("d1", payload)).thenReturn(payload);

            assertThat(controller.updateDataset("d1", payload)).isSameAs(payload);
            verify(datasetService).update("d1", payload);
        }

        /** 删除数据集委托服务层 */
        @Test
        void deleteDatasetShouldDelegate() {
            controller.deleteDataset("d1");

            verify(datasetService).delete("d1");
        }
    }

    @Nested
    class CaseEndpoints {

        /** 用例列表按数据集分页并包装 */
        @Test
        void listCasesShouldWrapPageResult() {
            EvalCaseEntity evalCase = new EvalCaseEntity();
            evalCase.setId("c1");
            evalCase.setDatasetId("d1");
            when(caseService.pageQuery("d1", 1, 10)).thenReturn(pageOf(evalCase, 1, 10));

            PageResult<EvalCaseEntity> result = controller.listCases("d1", 1, 10);

            assertThat(result.getList()).containsExactly(evalCase);
            assertThat(result.getTotal()).isEqualTo(1);
            verify(caseService).pageQuery("d1", 1, 10);
        }

        /** 创建用例时路径 datasetId 覆盖请求体中的值 */
        @Test
        void createCaseShouldOverrideDatasetIdFromPath() {
            EvalCaseEntity payload = new EvalCaseEntity();
            payload.setDatasetId("body-ds");
            payload.setQuestion("q1");
            when(caseService.create(payload)).thenReturn(payload);

            EvalCaseEntity created = controller.createCase("path-ds", payload);

            assertThat(created.getDatasetId()).isEqualTo("path-ds");
            verify(caseService).create(payload);
        }

        /** 更新与删除用例委托服务层 */
        @Test
        void updateAndDeleteCaseShouldDelegate() {
            EvalCaseEntity payload = new EvalCaseEntity();
            when(caseService.update("c1", payload)).thenReturn(payload);

            assertThat(controller.updateCase("c1", payload)).isSameAs(payload);
            controller.deleteCase("c1");

            verify(caseService).update("c1", payload);
            verify(caseService).delete("c1");
        }
    }

    /** 对话回流接口：接口层只负责把 messageId / 筛选条件递给服务层 */
    @Nested
    class ConversationImportEndpoints {

        /** 单条回流只递 messageId，幂等与报数都在服务层 */
        @Test
        void importCaseFromMessageShouldDelegateMessageId() {
            EvalCaseImportDTO request = new EvalCaseImportDTO();
            request.setMessageId("m1");
            EvalCaseEntity imported = new EvalCaseEntity();
            imported.setId("c1");
            when(caseService.importFromMessage("ds1", "m1")).thenReturn(imported);

            assertThat(controller.importCaseFromMessage("ds1", request)).isSameAs(imported);
            verify(caseService).importFromMessage("ds1", "m1");
        }

        /** 空请求体不能把接口打成 NPE：交服务层按「消息 ID 不能为空」拒绝 */
        @Test
        void importCaseFromMessageShouldTolerateEmptyBody() {
            when(caseService.importFromMessage("ds1", null))
                    .thenThrow(new ApiException("消息 ID 不能为空"));

            assertThatThrownBy(() -> controller.importCaseFromMessage("ds1", new EvalCaseImportDTO()))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("消息 ID 不能为空");
        }

        /** 批量回流透传筛选条件并原样回报数结果 */
        @Test
        void importCasesFromConversationsShouldDelegate() {
            EvalCaseImportDTO request = new EvalCaseImportDTO();
            request.setFeedback("dislike");
            request.setLimit(20);
            EvalImportResultDTO result = EvalImportResultDTO.builder()
                    .scanned(20).imported(3).duplicated(16).unusable(1).caseIds(List.of("c1")).build();
            when(caseService.importFromConversations("ds1", request)).thenReturn(result);

            assertThat(controller.importCasesFromConversations("ds1", request)).isSameAs(result);
            verify(caseService).importFromConversations("ds1", request);
        }
    }

    @Nested
    class RunEndpoints {

        /** 发起评估直接委托服务层并回传运行记录 */
        @Test
        void runEvalShouldDelegate() {
            Map<String, Object> config = Map.of("modelId", "m1", "topK", 5);
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            run.setStatus("pending");
            when(runService.run("d1", config)).thenReturn(run);

            assertThat(controller.runEval("d1", config)).isSameAs(run);
            verify(runService).run("d1", config);
        }

        /** 运行历史：dataset/status 非空时拼接过滤条件并按开始时间倒序 */
        @Test
        void listRunsShouldApplyFiltersAndOrder() {
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            when(runService.page(any(IPage.class), any(Wrapper.class))).thenReturn(pageOf(run, 1, 10));

            PageResult<EvalRunEntity> result = controller.listRuns("d1", "completed", 1, 10);

            assertThat(result.getList()).containsExactly(run);

            ArgumentCaptor<IPage<EvalRunEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<EvalRunEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(runService).page(pageCap.capture(), wrapperCap.capture());

            assertThat(((Page<?>) pageCap.getValue()).getCurrent()).isEqualTo(1);
            assertThat(((Page<?>) pageCap.getValue()).getSize()).isEqualTo(10);

            String sql = ((LambdaQueryWrapper<?>) wrapperCap.getValue()).getSqlSegment();
            assertThat(sql).contains("dataset_id").contains("status")
                    .containsIgnoringCase("ORDER BY start_time");
        }

        /** 运行历史：dataset/status 为空或空白时跳过对应过滤条件 */
        @Test
        void listRunsShouldSkipBlankFilters() {
            when(runService.page(any(IPage.class), any(Wrapper.class)))
                    .thenReturn(pageOf(new EvalRunEntity(), 1, 10));

            controller.listRuns("  ", null, 1, 10);

            ArgumentCaptor<Wrapper<EvalRunEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(runService).page(any(IPage.class), wrapperCap.capture());

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("dataset_id");
            assertThat(wrapper.getTargetSql()).doesNotContain("status");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }

        /** 报告概览与逐 case 明细分两个接口；明细不随轮询重传，删除 run 要级联掉明细行 */
        @Test
        void getReportAndDeleteRunShouldDelegate() {
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            when(runService.getReport("r1")).thenReturn(run);

            assertThat(controller.getRunReport("r1")).isSameAs(run);

            EvalResultEntity row = new EvalResultEntity();
            row.setRunId("r1");
            when(resultService.listByRun("r1")).thenReturn(List.of(row));
            assertThat(controller.listRunResults("r1")).containsExactly(row);

            controller.deleteRun("r1");

            verify(runService).getReport("r1");
            verify(resultService).listByRun("r1");
            verify(runService).deleteRun("r1");
        }

        /** 展开某行才拉正文：同 run 下的明细直接返回 */
        @Test
        void runResultDetailShouldReturnRowOfSameRun() {
            EvalResultEntity row = new EvalResultEntity();
            row.setId("er1");
            row.setRunId("r1");
            when(resultService.detail("er1")).thenReturn(row);

            assertThat(controller.runResultDetail("r1", "er1")).isSameAs(row);
        }

        /** 明细不存在或不属于这个 run 都要报错，不能把别人的回答递过去 */
        @Test
        void runResultDetailShouldRejectMissingOrForeignRow() {
            when(resultService.detail("er-missing")).thenReturn(null);
            assertThatThrownBy(() -> controller.runResultDetail("r1", "er-missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("评估明细不存在");

            EvalResultEntity foreign = new EvalResultEntity();
            foreign.setId("er1");
            foreign.setRunId("r-other");
            when(resultService.detail("er1")).thenReturn(foreign);
            assertThatThrownBy(() -> controller.runResultDetail("r1", "er1"))
                    .isInstanceOf(ApiException.class);
        }

        /** 取消运行直接委托服务层 */
        @Test
        void cancelRunShouldDelegate() {
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            when(runService.cancelRun("r1")).thenReturn(run);

            assertThat(controller.cancelRun("r1")).isSameAs(run);
            verify(runService).cancelRun("r1");
        }
    }

    @Nested
    class CompareEndpoints {

        /** 两个 ID 都给时按「基准在前」直接比 */
        @Test
        void compareShouldUseExplicitBaseline() {
            EvalRunCompareDTO compare = EvalRunCompareDTO.builder()
                    .fromRunId("r0").toRunId("r1").sameDataset(true).build();
            when(runService.compare("r0", "r1")).thenReturn(compare);

            assertThat(controller.compareRuns("r1", "r0")).isSameAs(compare);
            verify(runService).compare("r0", "r1");
        }

        /** 只选一个运行时往前找基准，且方向不能反：差值才是「改动带来的变化」 */
        @Test
        void compareShouldFallBackToPreviousRunAsBaseline() {
            EvalRunEntity previous = new EvalRunEntity();
            previous.setId("r0");
            when(runService.previousRun("r1")).thenReturn(previous);
            when(runService.compare("r0", "r1")).thenReturn(
                    EvalRunCompareDTO.builder().fromRunId("r0").toRunId("r1").build());

            assertThat(controller.compareRuns("r1", " ").getFromRunId()).isEqualTo("r0");
            verify(runService).compare("r0", "r1");
        }

        /** 首次评估没有上一个可比：服务层的报错原样透出，不拿不相干的运行来凑 */
        @Test
        void compareShouldSurfaceMissingBaseline() {
            when(runService.previousRun("r1")).thenThrow(new ApiException("没有可对比的上一次运行"));

            assertThatThrownBy(() -> controller.compareRuns("r1", null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("没有可对比的上一次运行");
        }
    }

    @Nested
    class OnlineEvalEndpoints {

        /** 手动跑一轮在线评测：条件原样透传，摘要直接返回 */
        @Test
        void scoreOnlineShouldDelegate() {
            OnlineEvalRequestDTO request = new OnlineEvalRequestDTO();
            request.setLimit(5);
            request.setJudgeModelId("m1");
            OnlineEvalSummaryDTO summary = OnlineEvalSummaryDTO.builder()
                    .pendingBefore(40L).sampled(5).scored(4).withoutScore(1).judgeTokens(1200L)
                    .metrics(Map.of("faithfulness", new OnlineEvalSummaryDTO.MetricAvg(0.8, 4)))
                    .build();
            when(onlineEvalService.score(request)).thenReturn(summary);

            assertThat(controller.scoreOnline(request)).isSameAs(summary);
            verify(onlineEvalService).score(request);
        }

        /** 空请求体不拦：limit / 判定模型 / 指标都由服务层取默认 */
        @Test
        void scoreOnlineShouldTolerateEmptyBody() {
            when(onlineEvalService.score(null)).thenReturn(
                    OnlineEvalSummaryDTO.builder().sampled(0).metrics(Map.of()).build());

            assertThat(controller.scoreOnline(null).getSampled()).isZero();
        }

        /** 积压水位：只读一个数，给面板做「在线评测还在不在跑」的红点 */
        @Test
        void pendingCountShouldDelegate() {
            when(onlineEvalService.pendingCount()).thenReturn(128L);

            assertThat(controller.onlinePendingCount()).isEqualTo(128L);
            verify(onlineEvalService).pendingCount();
        }
    }
}
