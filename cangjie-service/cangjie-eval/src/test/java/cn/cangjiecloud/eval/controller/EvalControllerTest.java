package cn.cangjiecloud.eval.controller;

import cn.cangjiecloud.common.domain.PageResult;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalRunService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalController} 单元测试：覆盖 11 个接口的委托、路径参数覆盖与分页包装。
 */
@ExtendWith(MockitoExtension.class)
class EvalControllerTest {

    @Mock
    private IEvalDatasetService datasetService;
    @Mock
    private IEvalCaseService caseService;
    @Mock
    private IEvalRunService runService;

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
        controller = new EvalController(datasetService, caseService, runService);
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

        /** 报告查询与删除委托服务层 */
        @Test
        void getReportAndDeleteRunShouldDelegate() {
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            when(runService.getReport("r1")).thenReturn(run);

            assertThat(controller.getRunReport("r1")).isSameAs(run);

            controller.deleteRun("r1");

            verify(runService).getReport("r1");
            verify(runService).removeById("r1");
        }
    }
}
