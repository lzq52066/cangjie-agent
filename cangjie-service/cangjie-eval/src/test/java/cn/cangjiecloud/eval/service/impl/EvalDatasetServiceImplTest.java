package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
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
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link EvalDatasetServiceImpl} 单元测试。
 * <p>
 * 通过 spy 拦截 ServiceImpl 继承的 save/updateById/removeById/page 方法，不连接数据库。
 */
@ExtendWith(MockitoExtension.class)
class EvalDatasetServiceImplTest {

    private EvalDatasetServiceImpl service;

    @BeforeAll
    static void installTableInfoCache() {
        // LambdaQueryWrapper 解析 SFunction -> 列名需要 TableInfo 缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.eval.mapper.EvalDatasetMapper");
        TableInfoHelper.initTableInfo(assistant, EvalDatasetEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new EvalDatasetServiceImpl());
    }

    private static EvalDatasetEntity dataset(String id, String name) {
        EvalDatasetEntity entity = new EvalDatasetEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setKnowledgeBaseId("kb-" + id);
        return entity;
    }

    @Nested
    class Create {

        /** 新建数据集时应强制重置用例计数为 0 并落库 */
        @Test
        void createShouldResetCaseCountAndSave() {
            EvalDatasetEntity entity = dataset(null, "回归集");
            entity.setCaseCount(99);
            doReturn(true).when(service).save(any(EvalDatasetEntity.class));

            EvalDatasetEntity created = service.create(entity);

            assertThat(created).isSameAs(entity);
            assertThat(created.getCaseCount()).isZero();
            verify(service).save(entity);
        }
    }

    @Nested
    class Update {

        /** 数据集不存在时应抛出 ApiException，且不执行更新 */
        @Test
        void updateShouldThrowWhenDatasetMissing() {
            doReturn(null).when(service).getById("d-missing");

            assertThatThrownBy(() -> service.update("d-missing", dataset(null, "x")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("数据集不存在");

            verify(service, never()).updateById(any(EvalDatasetEntity.class));
        }

        /** 更新成功时应回填 ID 并返回重新查询后的实体 */
        @Test
        void updateShouldFillIdAndReturnReloadedEntity() {
            EvalDatasetEntity existing = dataset("d1", "旧名称");
            EvalDatasetEntity refreshed = dataset("d1", "新名称");
            EvalDatasetEntity payload = dataset(null, "新名称");
            doReturn(existing).doReturn(refreshed).when(service).getById("d1");
            doReturn(true).when(service).updateById(any(EvalDatasetEntity.class));

            EvalDatasetEntity result = service.update("d1", payload);

            assertThat(payload.getId()).isEqualTo("d1");
            assertThat(result).isSameAs(refreshed);
            verify(service).updateById(payload);
        }
    }

    @Nested
    class Delete {

        /** 数据集不存在时应静默返回，不触发删除 */
        @Test
        void deleteShouldDoNothingWhenMissing() {
            doReturn(null).when(service).getById("d-missing");

            service.delete("d-missing");

            verify(service, never()).removeById(any(java.io.Serializable.class));
        }

        /** 存在时应删除并记录日志（此处只校验删除调用） */
        @Test
        void deleteShouldRemoveWhenExists() {
            doReturn(dataset("d1", "回归集")).when(service).getById("d1");
            doReturn(true).when(service).removeById(any(java.io.Serializable.class));

            service.delete("d1");

            verify(service).removeById("d1");
        }
    }

    @Nested
    class PageQuery {

        /** 无关键字时只保留排序；分页参数为空时回退到 1/10 */
        @Test
        void pageQueryShouldFallbackToDefaultsAndSkipKeywordFilter() {
            IPage<EvalDatasetEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            assertThat(service.pageQuery(null, null, null)).isSameAs(stub);

            ArgumentCaptor<IPage<EvalDatasetEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<EvalDatasetEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(1);
            assertThat(requested.getSize()).isEqualTo(10);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            assertThat(wrapper.getTargetSql()).doesNotContain("name");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY");
        }

        /** 关键字非空时应追加 name LIKE 条件并透传分页参数 */
        @Test
        void pageQueryShouldApplyKeywordAndExplicitPaging() {
            IPage<EvalDatasetEntity> stub = new Page<>(3, 5);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery(" 回归 ", 3, 5);

            ArgumentCaptor<IPage<EvalDatasetEntity>> pageCap = ArgumentCaptor.forClass(IPage.class);
            ArgumentCaptor<Wrapper<EvalDatasetEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(pageCap.capture(), wrapperCap.capture());

            Page<?> requested = (Page<?>) pageCap.getValue();
            assertThat(requested.getCurrent()).isEqualTo(3);
            assertThat(requested.getSize()).isEqualTo(5);

            LambdaQueryWrapper<?> wrapper = (LambdaQueryWrapper<?>) wrapperCap.getValue();
            String sql = wrapper.getSqlSegment();
            assertThat(sql).contains("name");
            assertThat(sql).containsIgnoringCase("LIKE");
            assertThat(sql).containsIgnoringCase("ORDER BY");
        }

        /** 空白关键字视为无过滤条件 */
        @Test
        void pageQueryShouldTreatBlankKeywordAsAbsent() {
            IPage<EvalDatasetEntity> stub = new Page<>(1, 10);
            doReturn(stub).when(service).page(any(IPage.class), any(Wrapper.class));

            service.pageQuery("   ", 1, 10);

            ArgumentCaptor<Wrapper<EvalDatasetEntity>> wrapperCap = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).page(any(IPage.class), wrapperCap.capture());
            assertThat(wrapperCap.getValue().getTargetSql()).doesNotContain("name");
        }
    }
}
