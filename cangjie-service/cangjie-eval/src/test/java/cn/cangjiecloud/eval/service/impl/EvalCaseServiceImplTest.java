package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
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

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link EvalCaseServiceImpl} 单元测试：覆盖创建校验、计数刷新与分页/列表查询。
 */
@ExtendWith(MockitoExtension.class)
class EvalCaseServiceImplTest {

    @Mock
    private IEvalDatasetService datasetService;

    private EvalCaseServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.eval.mapper.EvalCaseMapper");
        TableInfoHelper.initTableInfo(assistant, EvalCaseEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new EvalCaseServiceImpl(datasetService));
    }

    private static EvalCaseEntity evalCase(String id, String datasetId, String question) {
        EvalCaseEntity entity = new EvalCaseEntity();
        entity.setId(id);
        entity.setDatasetId(datasetId);
        entity.setQuestion(question);
        entity.setExpectedAnswer("期望答案");
        entity.setReferenceDocs("[\"doc1\"]");
        return entity;
    }

    @Nested
    class Create {

        /** datasetId 为 null/空白时拒绝创建，不落库 */
        @Test
        void createShouldRejectMissingDatasetId() {
            EvalCaseEntity nullDs = evalCase(null, null, "q1");
            EvalCaseEntity blankDs = evalCase(null, "  ", "q2");

            assertThatThrownBy(() -> service.create(nullDs))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("数据集 ID 不能为空");
            assertThatThrownBy(() -> service.create(blankDs))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("数据集 ID 不能为空");

            verify(service, never()).save(any(EvalCaseEntity.class));
            verify(datasetService, never()).updateById(any(EvalDatasetEntity.class));
        }

        /** 创建成功后应按用例总数回写数据集的 caseCount */
        @Test
        void createShouldSaveAndRefreshDatasetCaseCount() {
            EvalCaseEntity entity = evalCase("c1", "ds1", "什么是仓颉？");
            doReturn(true).when(service).save(any(EvalCaseEntity.class));
            doReturn(2L).when(service).count(any(Wrapper.class));

            EvalCaseEntity created = service.create(entity);

            assertThat(created).isSameAs(entity);
            verify(service).save(entity);

            ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
            verify(datasetService).updateById(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo("ds1");
            assertThat(captor.getValue().getCaseCount()).isEqualTo(2);
        }
    }

    @Nested
    class Update {

        /** 用例不存在时抛 ApiException */
        @Test
        void updateShouldThrowWhenCaseMissing() {
            doReturn(null).when(service).getById("c-missing");

            assertThatThrownBy(() -> service.update("c-missing", evalCase(null, "ds1", "q")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("用例不存在");

            verify(service, never()).updateById(any(EvalCaseEntity.class));
        }

        /** 更新时应忽略入参 datasetId，沿用原用例归属并返回重查结果 */
        @Test
        void updateShouldKeepOriginalDatasetIdAndReturnReloaded() {
            EvalCaseEntity existing = evalCase("c1", "ds-orig", "旧问题");
            EvalCaseEntity reloaded = evalCase("c1", "ds-orig", "新问题");
            EvalCaseEntity payload = evalCase(null, "ds-injected", "新问题");
            doReturn(existing).doReturn(reloaded).when(service).getById("c1");
            doReturn(true).when(service).updateById(any(EvalCaseEntity.class));

            EvalCaseEntity result = service.update("c1", payload);

            assertThat(payload.getId()).isEqualTo("c1");
            assertThat(payload.getDatasetId()).isEqualTo("ds-orig");
            assertThat(result).isSameAs(reloaded);
            verify(service).updateById(payload);
        }
    }

    @Nested
    class Delete {

        /** 用例不存在时静默返回 */
        @Test
        void deleteShouldDoNothingWhenMissing() {
            doReturn(null).when(service).getById("c-missing");

            service.delete("c-missing");

            verify(service, never()).removeById(any(Serializable.class));
            verify(datasetService, never()).updateById(any(EvalDatasetEntity.class));
        }

        /** 删除成功后按剩余用例数刷新数据集的 caseCount */
        @Test
        void deleteShouldRemoveAndRefreshCount() {
            doReturn(evalCase("c1", "ds1", "q")).when(service).getById("c1");
            doReturn(true).when(service).removeById(any(Serializable.class));
            doReturn(0L).when(service).count(any(Wrapper.class));

            service.delete("c1");

            verify(service).removeById("c1");
            ArgumentCaptor<EvalDatasetEntity> captor = ArgumentCaptor.forClass(EvalDatasetEntity.class);
            verify(datasetService).updateById(captor.capture());
            assertThat(captor.getValue().getCaseCount()).isZero();
        }
    }

    @Nested
    class Query {

        /** 分页参数为空回退 1/10，条件按数据集过滤并按创建时间升序 */
        @Test
        void pageQueryShouldUseDefaultsAndFilterByDataset() {
            IPage<EvalCaseEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            assertThat(service.pageQuery("ds1", null, null)).isSameAs(stub);

            ArgumentCaptor<IPage<EvalCaseEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<EvalCaseEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            String sql = wrapper.getSqlSegment();
            assertThat(sql).contains("dataset_id");
            assertThat(sql).containsIgnoringCase("ORDER BY create_time");
        }

        /** 显式分页参数应透传 */
        @Test
        void pageQueryShouldHonorExplicitPaging() {
            IPage<EvalCaseEntity> stub = new Page<>(2, 50);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery("ds1", 2, 50);

            ArgumentCaptor<IPage<EvalCaseEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            verify(service).page(pageCap.capture(), any(Wrapper.class));
            assertThat(((Page<?>) pageCap.getValue()).getCurrent()).isEqualTo(2);
            assertThat(((Page<?>) pageCap.getValue()).getSize()).isEqualTo(50);
        }

        /** listByDataset 走全量 list 并带数据集过滤条件 */
        @Test
        void listByDatasetShouldQueryAllCasesOfDataset() {
            List<EvalCaseEntity> stub = List.of(evalCase("c1", "ds1", "q1"), evalCase("c2", "ds1", "q2"));
            doReturn(stub).when(service).list(any(Wrapper.class));

            assertThat(service.listByDataset("ds1")).isEqualTo(stub);

            ArgumentCaptor<Wrapper<EvalCaseEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(wrapperCap.capture());
            assertThat(wrapperCap.getValue().getTargetSql()).contains("dataset_id");
        }
    }
}
