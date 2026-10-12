package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.conversation.ConversationFilter;
import cn.cangjiecloud.core.conversation.ConversationSample;
import cn.cangjiecloud.core.conversation.ConversationSource;
import cn.cangjiecloud.core.conversation.ConversationTurn;
import cn.cangjiecloud.eval.dto.EvalCaseImportDTO;
import cn.cangjiecloud.eval.dto.EvalImportResultDTO;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalCaseServiceImpl} 单元测试：覆盖创建校验、计数刷新、分页/列表查询与对话回流。
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

    /**
     * 对话回流：单条走幂等新增、批量走分栏报数。
     * <p>
     * 这里用真 {@link ConversationSource} 的测试替身（而不是 mock），因为要验的是
     * 「实现方交回来的样本怎么被拆成用例」，替身只要能按脚本发样本、能记下筛选条件就够了。
     */
    @Nested
    class ConversationImport {

        private List<ConversationSample> scripted;
        private ConversationFilter lastFilter;

        /** 按脚本回放样本：loadSample 按 messageId 命中，searchSamples 整包给出并记下筛选条件 */
        private void installSource() {
            scripted = new ArrayList<>();
            ConversationSource source = new ConversationSource() {
                @Override
                public ConversationSample loadSample(String messageId) {
                    return scripted.stream()
                            .filter(s -> s.messageId().equals(messageId))
                            .findFirst()
                            .orElse(null);
                }

                @Override
                public List<ConversationSample> searchSamples(ConversationFilter filter) {
                    lastFilter = filter;
                    return scripted;
                }
            };
            ReflectionTestUtils.setField(service, "conversationSource", source);
        }

        private void datasetExists() {
            when(datasetService.getById("ds1")).thenReturn(new EvalDatasetEntity());
        }

        private static ConversationSample sample(String messageId, String question) {
            return new ConversationSample(messageId, "s1", "app1", question, "AI 回答", List.of(),
                    null, null, List.of(), List.of(), LocalDateTime.of(2026, 10, 9, 10, 0));
        }

        /** 没装配对话模块时报「能力未启用」：静默返空列表会被读成「线上没有可回流的反馈」 */
        @Test
        void importShouldRejectWhenConversationModuleAbsent() {
            datasetExists();

            assertThatThrownBy(() -> service.importFromMessage("ds1", "m1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("对话回流能力未启用：未装配对话模块");
        }

        /** 数据集不存在时先于能力检查失败，避免把回流打到不存在的数据集上 */
        @Test
        void importShouldRejectMissingDataset() {
            when(datasetService.getById("ds-missing")).thenReturn(null);

            assertThatThrownBy(() -> service.importFromMessage("ds-missing", "m1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("数据集不存在: ds-missing");
        }

        @Test
        void importShouldRejectBlankMessageId() {
            datasetExists();
            installSource();

            assertThatThrownBy(() -> service.importFromMessage("ds1", "  "))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("消息 ID 不能为空");
            assertThatThrownBy(() -> service.importFromMessage("ds1", null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("消息 ID 不能为空");
        }

        /** 消息不存在 / 不是可回流的 role：由实现方返回 null，这里是这条边界 */
        @Test
        void importShouldRejectUnknownMessage() {
            datasetExists();
            installSource();

            assertThatThrownBy(() -> service.importFromMessage("ds1", "m-missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("对话消息不存在或不是可回流的回复: m-missing");
        }

        /** 配不到用户问题的回复（如开场白）不能当用例 */
        @Test
        void importShouldRejectUnpairedQuestion() {
            datasetExists();
            installSource();
            scripted.add(sample("m1", null));

            assertThatThrownBy(() -> service.importFromMessage("ds1", "m1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("该消息没有配对的用户问题，无法作为评测用例");
        }

        /** 幂等：同一条 AI 消息反复点「加入数据集」只算一票，否则均值被同一件事重复投票 */
        @Test
        void importShouldReturnExistingCaseWithoutInsertingAgain() {
            datasetExists();
            installSource();
            scripted.add(sample("m1", "什么是仓颉？"));
            EvalCaseEntity existing = evalCase("c-exist", "ds1", "什么是仓颉？");
            doReturn(existing).when(service).getOne(any(Wrapper.class), anyBoolean());

            assertThat(service.importFromMessage("ds1", " m1 ")).isSameAs(existing);

            verify(service, never()).save(any(EvalCaseEntity.class));
            verify(datasetService, never()).updateById(any(EvalDatasetEntity.class));
        }

        /** 样本 → 用例：标注当期望答案、检索来源当 ground truth、本轮之前的对话与幂等键落库 */
        @Test
        void importShouldConvertSampleToCase() {
            datasetExists();
            installSource();
            scripted.add(new ConversationSample("m1", "s1", "app1", "  什么是仓颉？  ", "AI 回答",
                    List.of(new ConversationTurn("user", "你们支持哪些模型？"),
                            new ConversationTurn("assistant", "支持 OpenAI 兼容协议与本地模型。")),
                    "dislike", "人工修正的答案", List.of("doc1", "doc1", " "), List.of("p1"),
                    LocalDateTime.of(2026, 10, 9, 10, 0)));
            doReturn(null).when(service).getOne(any(Wrapper.class), anyBoolean());
            doReturn(true).when(service).save(any(EvalCaseEntity.class));
            doReturn(3L).when(service).count(any(Wrapper.class));

            service.importFromMessage("ds1", "m1");

            ArgumentCaptor<EvalCaseEntity> captor = ArgumentCaptor.forClass(EvalCaseEntity.class);
            verify(service).save(captor.capture());
            EvalCaseEntity saved = captor.getValue();
            assertThat(saved.getDatasetId()).isEqualTo("ds1");
            assertThat(saved.getQuestion()).isEqualTo("什么是仓颉？");
            assertThat(saved.getExpectedAnswer()).isEqualTo("人工修正的答案");
            assertThat(saved.getReferenceDocs()).isEqualTo("[\"doc1\"]");
            assertThat(saved.getReferenceChunks()).isEqualTo("[\"p1\"]");
            // 点踩的那批修没修好，是这套闭环唯一的验收口径，得能单独切片看
            assertThat(saved.getTags()).isEqualTo("from-chat,dislike");
            assertThat(saved.getSource()).isEqualTo("trace");
            assertThat(saved.getSourceMessageId()).isEqualTo("m1");
            // 追问脱离上文就评不出东西：这一轮之前的对话要原样进用例，跑评测时一起发给模型
            assertThat(saved.getConversation()).isEqualTo(
                    "[{\"role\":\"user\",\"content\":\"你们支持哪些模型？\"},"
                            + "{\"role\":\"assistant\",\"content\":\"支持 OpenAI 兼容协议与本地模型。\"}]");

            ArgumentCaptor<EvalDatasetEntity> dsCap = ArgumentCaptor.forClass(EvalDatasetEntity.class);
            verify(datasetService).updateById(dsCap.capture());
            assertThat(dsCap.getValue().getCaseCount()).isEqualTo(3);
        }

        /** 没有人工标注就没有标准答案；空列表写 null，一个 "[]" 与「没有标注」应是同一个样子 */
        @Test
        void importShouldLeaveExpectedAnswerAndReferencesEmptyWhenAbsent() {
            datasetExists();
            installSource();
            scripted.add(sample("m1", "q1"));
            doReturn(null).when(service).getOne(any(Wrapper.class), anyBoolean());
            doReturn(true).when(service).save(any(EvalCaseEntity.class));
            doReturn(1L).when(service).count(any(Wrapper.class));

            service.importFromMessage("ds1", "m1");

            ArgumentCaptor<EvalCaseEntity> captor = ArgumentCaptor.forClass(EvalCaseEntity.class);
            verify(service).save(captor.capture());
            assertThat(captor.getValue().getExpectedAnswer()).isNull();
            assertThat(captor.getValue().getReferenceDocs()).isNull();
            assertThat(captor.getValue().getReferenceChunks()).isNull();
            assertThat(captor.getValue().getTags()).isEqualTo("from-chat");
            // 单轮问答没有上文，这一列留 null，别在报告里冒充「有一段空历史」
            assertThat(captor.getValue().getConversation()).isNull();
        }

        /** 后台勾选走 messageIds：按 ID 精确取样本，一条筛选都不跑，取不到的计入 unusable */
        @Test
        void batchImportShouldUseSelectedMessageIdsInsteadOfFilters() {
            datasetExists();
            installSource();
            scripted.add(sample("m1", "q1"));
            scripted.add(sample("m2", "q2"));
            doReturn(List.of()).when(service).list(any(Wrapper.class));
            doAnswer(inv -> {
                List<EvalCaseEntity> batch = inv.getArgument(0);
                batch.forEach(item -> item.setId("c-" + item.getSourceMessageId()));
                return true;
            }).when(service).saveBatch(anyCollection());
            doReturn(2L).when(service).count(any(Wrapper.class));

            EvalCaseImportDTO request = new EvalCaseImportDTO();
            request.setMessageIds(List.of("m1", " m2 ", "m1", "m-missing"));

            EvalImportResultDTO result = service.importFromConversations("ds1", request);

            assertThat(lastFilter).isNull();
            // 选中列表去空去重后是 m1 / m2 / m-missing 三条
            assertThat(result.getScanned()).isEqualTo(3);
            assertThat(result.getImported()).isEqualTo(2);
            assertThat(result.getUnusable()).isEqualTo(1);
            assertThat(result.getCaseIds()).containsExactly("c-m1", "c-m2");
        }

        /** 批量按 scanned / imported / duplicated / unusable 分栏报数，只刷一次计数 */
        @Test
        void batchImportShouldCountByCategory() {
            datasetExists();
            installSource();
            scripted.add(sample("m1", "q1"));
            scripted.add(sample("m1", "q1-重复"));                       // 本批内重复
            scripted.add(sample("m2", "q2"));                            // 历史已回流
            scripted.add(sample("m3", null));                            // 配不到问题
            EvalCaseEntity imported = evalCase("c-old", "ds1", "q2");
            imported.setSourceMessageId("m2");
            doReturn(List.of(imported)).when(service).list(any(Wrapper.class));
            doAnswer(inv -> {
                List<EvalCaseEntity> batch = inv.getArgument(0);
                batch.get(0).setId("c-new");
                return true;
            }).when(service).saveBatch(anyCollection());
            doReturn(4L).when(service).count(any(Wrapper.class));

            EvalImportResultDTO result = service.importFromConversations("ds1", new EvalCaseImportDTO());

            assertThat(result.getScanned()).isEqualTo(4);
            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getDuplicated()).isEqualTo(2);
            assertThat(result.getUnusable()).isEqualTo(1);
            assertThat(result.getCaseIds()).containsExactly("c-new");

            verify(service).saveBatch(anyCollection());
            // 逐条 create 会把「导 50 条」做成 50 次 count + 50 次数据集回写
            verify(service, never()).save(any(EvalCaseEntity.class));
            verify(datasetService, times(1)).updateById(any(EvalDatasetEntity.class));
        }

        /** 一条候选都没有时不写库、不回写计数，但照样报数 */
        @Test
        void batchImportShouldDoNothingWhenNoCandidate() {
            datasetExists();
            installSource();
            doReturn(List.of()).when(service).list(any(Wrapper.class));

            EvalImportResultDTO result = service.importFromConversations("ds1", null);

            assertThat(result.getScanned()).isZero();
            assertThat(result.getCaseIds()).isEmpty();
            verify(service, never()).saveBatch(anyCollection());
            verify(datasetService, never()).updateById(any(EvalDatasetEntity.class));
            // 入参为 null 等于「不限条件」，四个筛选位都该是 null 而不是把 false 当缺省哨兵
            assertThat(lastFilter).isEqualTo(new ConversationFilter(null, null, null, null));
        }

        /** 筛选条件透传：空串归一成 null，前后空格清掉，limit 交实现方收敛 */
        @Test
        void batchImportShouldNormalizeFilterConditions() {
            datasetExists();
            installSource();
            doReturn(List.of()).when(service).list(any(Wrapper.class));
            EvalCaseImportDTO request = new EvalCaseImportDTO();
            request.setApplicationId("  app1 ");
            request.setFeedback("");
            request.setAnnotatedOnly(Boolean.TRUE);
            request.setLimit(20);

            service.importFromConversations("ds1", request);

            assertThat(lastFilter).isEqualTo(new ConversationFilter("app1", null, Boolean.TRUE, 20));
        }
    }
}
