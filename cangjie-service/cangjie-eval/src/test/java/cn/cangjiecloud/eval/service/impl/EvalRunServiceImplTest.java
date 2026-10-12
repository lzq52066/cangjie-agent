package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalOptions;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.eval.dto.EvalRunCompareDTO;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalResultEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.metric.EvalMetricRegistry;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalResultService;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalRunServiceImpl} 单元测试。
 * <p>
 * 用「捕获型 Executor」替代 businessExecutor：{@code run()} 只登记异步任务，
 * 测试中显式执行该任务，从而以确定性方式验证评估主流程、进度与异常分支。
 * <p>
 * 逐 case 结果断言看的是 {@code eval_result} 明细行（经 resultService.record 落库），
 * 不再从 eval_run.results 那个整包 JSON 里拆。
 * <p>
 * 指标注册表默认塞空集合：既有用例断言的是「只跑了 recall / correctness」的老口径，
 * 若默认把四个 RAGAS 指标都打开，判定模型没配时会成片出 failed，把状态判定整体带跑。
 * SPI 指标的接线由 {@code MetricSpiWiring} 单独覆盖。
 */
@ExtendWith(MockitoExtension.class)
class EvalRunServiceImplTest {

    @Mock
    private IEvalDatasetService datasetService;
    @Mock
    private IEvalCaseService caseService;
    @Mock
    private HybridRetriever hybridRetriever;
    @Mock
    private IModelService modelService;
    @Mock
    private IEvalResultService resultService;
    @Mock
    private OpenAICompatibleClient modelClient;

    private EvalMetricRegistry metricRegistry;

    private EvalRunServiceImpl service;
    private AtomicReference<Runnable> pendingTask;

    @BeforeAll
    static void installTableInfoCache() {
        // 进度单列更新用 LambdaUpdateWrapper.set(实体::getX) 解析列名，需要 TableInfo 缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("cn.cangjiecloud.eval.mapper.EvalRunMapper");
        TableInfoHelper.initTableInfo(assistant, EvalRunEntity.class);
    }

    @BeforeEach
    void setUp() {
        pendingTask = new AtomicReference<>();
        metricRegistry = new EvalMetricRegistry(List.of());
        service = newService(metricRegistry);
        // 明细默认写成功；个别用例用 doReturn(false) 覆盖成「落库失败」
        lenient().when(resultService.record(any(EvalResultEntity.class))).thenReturn(true);
    }

    /**
     * 换一个指标注册表重建服务：指标接线用例需要在 setUp 之后重新装配。
     * <p>
     * 主键回写跟着实例走（不是 lenient 的会触发 UnnecessaryStubbing，故放在这里统一处理）：
     * 取消 / 删除的登记链路全靠 save 后拿到真实 runId。
     */
    private EvalRunServiceImpl newService(EvalMetricRegistry registry) {
        EvalRunServiceImpl instance = spy(new EvalRunServiceImpl(datasetService, caseService, hybridRetriever,
                modelService, resultService, registry, pendingTask::set));
        lenient().doAnswer(invocation -> {
            ((EvalRunEntity) invocation.getArgument(0)).setId("r1");
            return true;
        }).when(instance).save(any(EvalRunEntity.class));
        return instance;
    }

    private static EvalDatasetEntity dataset(String id, String knowledgeBaseId, String name) {
        EvalDatasetEntity dataset = new EvalDatasetEntity();
        dataset.setId(id);
        dataset.setKnowledgeBaseId(knowledgeBaseId);
        dataset.setName(name);
        return dataset;
    }

    private static EvalCaseEntity evalCase(String id, String question, String referenceDocs) {
        EvalCaseEntity entity = new EvalCaseEntity();
        entity.setId(id);
        entity.setDatasetId("ds1");
        entity.setQuestion(question);
        entity.setExpectedAnswer("期望回答");
        entity.setReferenceDocs(referenceDocs);
        return entity;
    }

    private static RetrievalResult retrieval(String documentId, String content, double score) {
        return RetrievalResult.builder().documentId(documentId).content(content).finalScore(score).build();
    }

    /** 执行 run() 登记的异步任务 */
    private void executePendingTask() {
        assertThat(pendingTask.get()).as("异步评估任务应已登记").isNotNull();
        pendingTask.get().run();
    }

    private List<EvalResultEntity> recordedResults() {
        ArgumentCaptor<EvalResultEntity> captor = ArgumentCaptor.forClass(EvalResultEntity.class);
        verify(resultService, atLeastOnce()).record(captor.capture());
        return captor.getAllValues();
    }

    private EvalResultEntity singleRecordedResult() {
        List<EvalResultEntity> results = recordedResults();
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private static Map<String, Object> summaryOf(EvalRunEntity run) {
        return JsonUtils.parseMap(run.getSummary());
    }

    /**
     * 捕获真正传给检索的口径。
     * <p>
     * 评测与生产已共一条检索路径，config 里的 threshold / searchMode / rerank 到底传没传，
     * 只能从调用参数上验，不能拿分数倒推。
     */
    private RetrievalOptions capturedOptions() {
        ArgumentCaptor<RetrievalOptions> captor = ArgumentCaptor.forClass(RetrievalOptions.class);
        verify(hybridRetriever).retrieve(anyString(), captor.capture());
        return captor.getValue();
    }

    @Nested
    class RunValidation {

        /** 数据集不存在时直接失败，不创建运行记录 */
        @Test
        void runShouldRejectMissingDataset() {
            when(datasetService.getById("ds-missing")).thenReturn(null);

            assertThatThrownBy(() -> service.run("ds-missing", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("数据集不存在");

            verify(caseService, never()).listByDataset(anyString());
            verify(service, never()).save(any(EvalRunEntity.class));
        }

        /** 数据集无用例时拒绝启动评估 */
        @Test
        void runShouldRejectDatasetWithoutCases() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "空集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of());

            assertThatThrownBy(() -> service.run("ds1", Map.of()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("数据集没有评估用例");

            verify(service, never()).save(any(EvalRunEntity.class));
        }
    }

    @Nested
    class RunHappyPath {

        /** 启动时先落 pending 快照，异步执行完成后回填汇总，逐 case 结果写进明细表 */
        @Test
        void runShouldPersistPendingThenCompleteWithSummaryAndRecall() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "什么是仓颉？", "[\"doc1\",\"doc2\"]")));
            when(hybridRetriever.retrieve(eq("什么是仓颉？"), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc1", "仓颉是国产编程语言", 0.92),
                            retrieval("doc3", "无关内容", 0.30)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            Map<String, Object> config = new HashMap<>();
            config.put("topK", 3);
            EvalRunEntity run = service.run("ds1", config);

            // run() 返回时异步任务尚未执行：pending 快照
            assertThat(run.getStatus()).isEqualTo("pending");
            assertThat(run.getProgress()).isZero();
            assertThat(run.getConfigSnapshot()).contains("topK");
            assertThat(run.getDatasetId()).isEqualTo("ds1");
            verify(service).save(run);
            verify(service, never()).updateById(any(EvalRunEntity.class));

            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("completed");
            assertThat(run.getProgress()).isEqualTo(100);
            assertThat(run.getStartTime()).isNotNull();
            assertThat(run.getEndTime()).isNotNull();
            // 逐 case 结果已拆表，run 行上不再挂整包 JSON
            assertThat(run.getResults()).isNull();

            Map<String, Object> summary = summaryOf(run);
            assertThat(summary).containsEntry("totalCases", 1)
                    .containsEntry("completed", 1)
                    .containsEntry("failed", 0)
                    .containsEntry("totalTokens", 0)
                    .containsEntry("recallCases", 1);
            assertThat(summary.get("avgRecall")).isEqualTo("0.5000");
            // 没配 modelId 就没跑判定：均值缺席而不是伪造一个 0 分
            assertThat(summary.get("avgCorrectness")).isNull();
            assertThat(summary.get("correctnessCases")).isEqualTo(0);

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getRunId()).isEqualTo(run.getId());
            assertThat(result.getCaseId()).isEqualTo("c1");
            assertThat(result.getDatasetId()).isEqualTo("ds1");
            assertThat(result.getQuestion()).isEqualTo("什么是仓颉？");
            assertThat(result.getRecall()).isEqualTo(0.5);
            assertThat(result.getCorrectness()).isNull();
            assertThat(result.getStatus()).isEqualTo("success");
            assertThat(result.getError()).isNull();
            assertThat(result.getLatency()).isNotNull();
            assertThat(result.getTraceId()).startsWith("eval-").endsWith("-c1");

            List<Map<String, Object>> contexts = JsonUtils.parseListOfMap(result.getContexts());
            assertThat(contexts).hasSize(2);
            assertThat(contexts.get(0)).containsEntry("rank", 1)
                    .containsEntry("documentId", "doc1")
                    .containsEntry("content", "仓颉是国产编程语言");
            assertThat(contexts.get(0).get("score")).isEqualTo(0.92);

            Map<String, Object> detail = JsonUtils.parseMap(result.getDetail());
            assertThat(detail).hasEntrySatisfying("hitDocs",
                    v -> assertThat((List<String>) v).containsExactly("doc1"));
            assertThat(detail).hasEntrySatisfying("missedDocs",
                    v -> assertThat((List<String>) v).containsExactly("doc2"));

            // 进度单列更新（1 条用例即 100%）+ 终态汇总整行
            verify(service, times(1)).update(any(Wrapper.class));
            verify(service, times(2)).updateById(run);
        }

        /** topK 解不出来（非数字、非正数）时才回落到默认 5 */
        @Test
        void runShouldFallbackToDefaultTopKWhenConfigInvalid() {
            stubSingleCaseRetrieval();

            Map<String, Object> config = new HashMap<>();
            config.put("topK", "很多");
            service.run("ds1", config);
            executePendingTask();

            // 口径不丢：知识库范围进 options，无效 topK 回落到默认 5
            RetrievalOptions options = capturedOptions();
            assertThat(options.getKnowledgeBaseIds()).containsExactly("kb1");
            assertThat(options.getTopK()).isEqualTo(5);
            // 阈值 / 模式 / 重排没传就留 null，由检索侧按知识库配置解析，评测不替生产做主
            assertThat(options.getSimilarityThreshold()).isNull();
            assertThat(options.getSearchMode()).isNull();
            assertThat(options.getRerankType()).isNull();
            // 无参考文档 → 召回率不评测，均值缺席且分母为 0
            assertThat(summaryOf(capturedRun()).get("avgRecall")).isNull();
        }

        /** 前端表单交上来的是字符串数字，不能当成「没传」静默回落成默认值 */
        @Test
        void runShouldAcceptNumericStringFromConfigAndPassRetrievalOptionsThrough() {
            stubSingleCaseRetrieval();

            Map<String, Object> config = new HashMap<>();
            config.put("topK", "3");
            config.put("similarityThreshold", "0.5");
            config.put("searchMode", "two_stage");
            config.put("rerankType", "weighted");
            service.run("ds1", config);
            executePendingTask();

            RetrievalOptions options = capturedOptions();
            assertThat(options.getTopK()).isEqualTo(3);
            assertThat(options.getSimilarityThreshold()).isEqualTo(0.5);
            assertThat(options.getSearchMode()).isEqualTo("two_stage");
            assertThat(options.getRerankType()).isEqualTo("weighted");
        }

        /** 多知识库优先取 config，没取到才回落数据集绑定的那一个 */
        @Test
        void runShouldPreferConfigKnowledgeBaseIdsOverDataset() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(hybridRetriever.retrieve(eq("q1"), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc9", "内容", 0.5)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            Map<String, Object> config = new HashMap<>();
            // 逗号串与数组两种形状都得认：前端表单拼串是常态
            config.put("knowledgeBaseIds", "kb9,kb8,kb9");
            service.run("ds1", config);
            executePendingTask();

            assertThat(capturedOptions().getKnowledgeBaseIds()).containsExactly("kb9", "kb8");
        }

        private void stubSingleCaseRetrieval() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(hybridRetriever.retrieve(eq("q1"), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc9", "内容", 0.5)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));
        }

        /** 数据集未绑定知识库时跳过检索，召回率记为「未评测」而非 0 分 */
        @Test
        void runShouldSkipRetrievalWhenDatasetHasNoKnowledgeBase() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "无知识库集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", "[\"doc1\"]")));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            verify(hybridRetriever, never()).retrieve(anyString(), any(RetrievalOptions.class));

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getRecall()).isNull();
            assertThat(result.getStatus()).isEqualTo("partial");
            assertThat(JsonUtils.parseMap(result.getDetail())).containsKey("recallSkipped");
            assertThat(summaryOf(run).get("avgRecall")).isNull();
            assertThat(summaryOf(run).get("recallCases")).isEqualTo(0);
        }

        /** 进度改为节流单列更新：不再每个 case 都 updateById 重写整行 */
        @Test
        void runShouldUpdateProgressInThrottledSingleColumnWrites() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "三用例集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", null), evalCase("c2", "q2", null), evalCase("c3", "q3", null)));

            // updateById 传入的是同一个可变 run 实例，需在调用时刻快照进度与状态
            List<Integer> progressSnapshots = new ArrayList<>();
            List<String> statusSnapshots = new ArrayList<>();
            doAnswer(invocation -> {
                EvalRunEntity updated = invocation.getArgument(0);
                progressSnapshots.add(updated.getProgress());
                statusSnapshots.add(updated.getStatus());
                return true;
            }).when(service).updateById(any(EvalRunEntity.class));
            List<Integer> progressUpdates = new ArrayList<>();
            doAnswer(invocation -> {
                LambdaUpdateWrapper<EvalRunEntity> wrapper = invocation.getArgument(0);
                // SET 片段只该有 progress 一列：连带重写 summary / 大字段就是旧写法的性能陷井
                assertThat(wrapper.getSqlSet()).containsOnlyOnce("progress");
                progressUpdates.add(1);
                return true;
            }).when(service).update(any(Wrapper.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            // 整行重写只剩「开始执行」与「终态汇总」两次
            assertThat(progressSnapshots).containsExactly(0, 100);
            assertThat(statusSnapshots).containsExactly("running", "completed");
            // 33% / 66% / 100% 各自跨过 5% 阈值，三次单列更新
            assertThat(progressUpdates).hasSize(3);
            assertThat(run.getEndTime()).isAfterOrEqualTo(run.getStartTime());
        }

        private EvalRunEntity capturedRun() {
            ArgumentCaptor<EvalRunEntity> captor = ArgumentCaptor.forClass(EvalRunEntity.class);
            verify(service, times(2)).updateById(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    class RunWithLlm {

        /** 配置 modelId 时生成回答并做 LLM-as-judge，判定 token 一并计入成本 */
        @Test
        void runShouldGenerateAnswerAndJudgeScore() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "什么是仓颉？", null)));
            when(hybridRetriever.retrieve(eq("什么是仓颉？"), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc1", "仓颉是国产编程语言", 0.9)));
            when(modelService.getClient("m1")).thenReturn(modelClient);
            when(modelClient.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("仓颉是华为推出的编程语言").totalTokens(12).build())
                    .thenReturn(ChatResponse.builder()
                            .content("{\"score\":0.85,\"reason\":\"关键事实一致\"}")
                            .promptTokens(3).completionTokens(2).totalTokens(5).build());
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            Map<String, Object> summary = summaryOf(run);
            // 判定那一路的 token 此前被漏掉：12 + 5 才是本次评估的真实花费
            assertThat(summary).containsEntry("totalTokens", 17);
            assertThat(summary.get("avgCorrectness")).isEqualTo("0.85");
            assertThat(summary.get("correctnessCases")).isEqualTo(1);

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getAnswer()).isEqualTo("仓颉是华为推出的编程语言");
            assertThat(result.getCorrectness()).isEqualTo(0.85);
            assertThat(result.getStatus()).isEqualTo("success");
            assertThat(result.getError()).isNull();
            assertThat(result.getTotalTokens()).isEqualTo(17L);
            assertThat(result.getInputTokens()).isEqualTo(3L);
            assertThat(result.getOutputTokens()).isEqualTo(2L);
            assertThat(JsonUtils.parseMap(result.getDetail()))
                    .containsEntry("judgeReason", "关键事实一致");

            // 第一次请求为生成回答（temperature 0.1 + 检索上下文），第二次为评分（temperature 0.0）
            ArgumentCaptor<ChatRequest> requestCap = ArgumentCaptor.forClass(ChatRequest.class);
            verify(modelClient, times(2)).chat(requestCap.capture());
            ChatRequest answerRequest = requestCap.getAllValues().get(0);
            assertThat(answerRequest.getTemperature()).isEqualTo(0.1);
            assertThat(answerRequest.getMessages()).hasSize(2);
            assertThat(answerRequest.getMessages().get(0).getRole()).isEqualTo("system");
            assertThat(answerRequest.getMessages().get(1).getContent())
                    .contains("参考资料").contains("仓颉是国产编程语言").contains("什么是仓颉？");

            ChatRequest judgeRequest = requestCap.getAllValues().get(1);
            assertThat(judgeRequest.getTemperature()).isEqualTo(0.0);
            assertThat(judgeRequest.getMessages().get(1).getContent()).contains("期望回答");
            // 判定要结构化输出，靠正则抠自由文本迟早翻车
            assertThat(judgeRequest.getResponseSchema()).contains("\"score\"").contains("\"reason\"");
            assertThat(judgeRequest.getResponseSchemaName()).isEqualTo("eval_judge");

            // 两次模型调用挂同一条 trace，报告才能一键跳到链路；runId 属于 agent_run 语义，不能挪用
            assertThat(answerRequest.getTraceContext()).isNotNull();
            assertThat(answerRequest.getTraceContext().getTraceId())
                    .isEqualTo(judgeRequest.getTraceContext().getTraceId())
                    .startsWith("eval-").endsWith("-c1");
            assertThat(answerRequest.getTraceContext().getRequestId()).endsWith("-answer");
            assertThat(judgeRequest.getTraceContext().getRequestId()).endsWith("-judge");
            assertThat(judgeRequest.getTraceContext().getModelId()).isEqualTo("m1");
            assertThat(judgeRequest.getTraceContext().getRunId()).isNull();
            assertThat(result.getTraceId()).isEqualTo(answerRequest.getTraceContext().getTraceId());
        }

        /** 判定输出解析不出分数时按「未评测」处理，不再用 0.5 中性分污染均值 */
        @Test
        void runShouldLeaveCorrectnessUnscoredWhenJudgeOutputUnparseable() {
            stubSingleCaseWithModel("无法评分");

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getCorrectness()).isNull();
            assertThat(result.getStatus()).isEqualTo("partial");
            assertThat(JsonUtils.parseMap(result.getDetail())).containsKey("judgeError");

            Map<String, Object> summary = summaryOf(capturedRun());
            assertThat(summary.get("avgCorrectness")).isNull();
            assertThat(summary.get("correctnessCases")).isEqualTo(0);
        }

        /** 判定走 0~10 之类非法分制时判为无效，不做臆测换算也不硬夹成 1.0 */
        @Test
        void runShouldRejectOutOfRangeJudgeScore() {
            stubSingleCaseWithModel("8.5");

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getCorrectness()).isNull();
            assertThat(JsonUtils.parseMap(result.getDetail())).containsKey("judgeError");
        }

        /** 结构化 JSON 之外的「带文字的分数」也能兜底解析 */
        @Test
        void runShouldParseScoreFromFreeTextWhenSchemaIgnored() {
            stubSingleCaseWithModel("Score: 0.7，因为关键事实齐全");

            assertThat(singleRecordedResult().getCorrectness()).isEqualTo(0.7);
        }

        /** 模型调用抛异常时该用例标记失败，整体仍完成且不计正确率 */
        @Test
        void runShouldMarkCaseFailedWhenLlmThrows() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(modelService.getClient("m1")).thenThrow(new RuntimeException("模型不可用"));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("completed");
            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getAnswer()).isNull();
            assertThat(result.getCorrectness()).isNull();
            assertThat(result.getStatus()).isEqualTo("fail");
            assertThat(result.getError()).isEqualTo("LLM 评估失败: 模型不可用");

            Map<String, Object> summary = summaryOf(run);
            assertThat(summary.get("avgCorrectness")).isNull();
            assertThat(summary).containsEntry("totalTokens", 0).containsEntry("failed", 1);
        }

        private void stubSingleCaseWithModel(String judgeOutput) {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "回归集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(evalCase("c1", "q1", null)));
            when(modelService.getClient("m1")).thenReturn(modelClient);
            when(modelClient.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("生成回答").totalTokens(7).build())
                    .thenReturn(ChatResponse.builder().content(judgeOutput).totalTokens(3).build());
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            service.run("ds1", config);
            executePendingTask();
        }

        private EvalRunEntity capturedRun() {
            ArgumentCaptor<EvalRunEntity> captor = ArgumentCaptor.forClass(EvalRunEntity.class);
            verify(service, times(2)).updateById(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    class RunResilience {

        /** 检索异常被用例级捕获：记录 error 并继续执行后续用例 */
        @Test
        void runShouldRecordErrorWhenRetrievalThrows() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", "[\"doc1\"]"), evalCase("c2", "q2", "[\"doc2\"]")));
            when(hybridRetriever.retrieve(eq("q1"), any(RetrievalOptions.class)))
                    .thenThrow(new RuntimeException("向量库超时"));
            when(hybridRetriever.retrieve(eq("q2"), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc2", "内容", 0.7)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            List<EvalResultEntity> results = recordedResults();
            assertThat(results).hasSize(2);
            assertThat(results.get(0).getStatus()).isEqualTo("fail");
            assertThat(results.get(0).getError()).isEqualTo("检索失败: 向量库超时");
            assertThat(results.get(0).getRecall()).isNull();
            assertThat(results.get(1).getRecall()).isEqualTo(1.0);
            assertThat(results.get(1).getStatus()).isEqualTo("success");

            assertThat(run.getStatus()).isEqualTo("completed");
            Map<String, Object> summary = summaryOf(run);
            assertThat(summary).containsEntry("completed", 2).containsEntry("failed", 1);
            // 失败的那条不出分，均值只按出分的用例算，不会被 0 分拖低
            assertThat(summary.get("avgRecall")).isEqualTo("1.0000");
            assertThat(summary.get("recallCases")).isEqualTo(1);
        }

        /** 删除运行时级联删明细，否则孤儿行会一直混进按数据集的聚合统计 */
        @Test
        void deleteRunShouldCascadeResults() {
            doReturn(completedRun("r1")).when(service).getById("r1");
            doReturn(true).when(service).removeById("r1");

            service.deleteRun("r1");

            verify(resultService).removeByRun("r1");
            verify(service).removeById("r1");
        }

        /** 不存在的 run 不能静默成功，否则前端看起来「删了」而列表里一行没动 */
        @Test
        void deleteRunShouldRejectMissingRun() {
            doReturn(null).when(service).getById("r-gone");

            assertThatThrownBy(() -> service.deleteRun("r-gone"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("评估运行不存在");

            verify(resultService, never()).removeByRun(anyString());
            verify(service, never()).removeById(anyString());
        }

        /** 真正在跑的运行删不掉：否则任务会继续烧完剩下 case 的 token，并往已删的 run 名下插孤儿明细 */
        @Test
        void deleteRunShouldRejectActivelyRunningRun() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(evalCase("c1", "q1", null)));
            doReturn(runningRun("r1")).when(service).getById("r1");
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));
            List<String> outcome = new ArrayList<>();
            when(hybridRetriever.retrieve(anyString(), any(RetrievalOptions.class))).thenAnswer(invocation -> {
                // 在任务执行中途发起删除：此刻 run 确实在本进程里跑着
                try {
                    service.deleteRun("r1");
                    outcome.add("deleted");
                } catch (ApiException e) {
                    outcome.add(e.getMessage());
                }
                return List.of();
            });

            service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(outcome).containsExactly("评估正在执行，请先停止后再删除");
            verify(service, never()).removeById(anyString());
        }

        /** 服务重启留下的僵尸行（本进程没有对应任务）仍然可以删，不能被「运行中」卡住 */
        @Test
        void deleteRunShouldAllowStaleRunningRun() {
            doReturn(runningRun("r1")).when(service).getById("r1");
            doReturn(true).when(service).removeById("r1");

            service.deleteRun("r1");

            verify(resultService).removeByRun("r1");
            verify(service).removeById("r1");
        }

        /** 僵尸 run 取消时直接收敛终态，不然永远停在「执行中」 */
        @Test
        void cancelRunShouldConvergeStaleRun() {
            doReturn(runningRun("r1")).when(service).getById("r1");
            doReturn(true).when(service).update(any(Wrapper.class));

            EvalRunEntity cancelled = service.cancelRun("r1");

            assertThat(cancelled.getStatus()).isEqualTo("cancelled");
            ArgumentCaptor<Wrapper<EvalRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(captor.capture());
            assertThat(((LambdaUpdateWrapper<EvalRunEntity>) captor.getValue()).getSqlSet())
                    .contains("status");
        }

        /** 已结束的运行无需取消 */
        @Test
        void cancelRunShouldRejectTerminalRun() {
            doReturn(completedRun("r1")).when(service).getById("r1");

            assertThatThrownBy(() -> service.cancelRun("r1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("运行已结束");
        }

        private static EvalRunEntity completedRun(String id) {
            EvalRunEntity run = new EvalRunEntity();
            run.setId(id);
            run.setStatus("completed");
            return run;
        }

        private static EvalRunEntity runningRun(String id) {
            EvalRunEntity run = new EvalRunEntity();
            run.setId(id);
            run.setStatus("running");
            return run;
        }
    }

    @Nested
    class TerminalState {

        /**
         * M1：异常没有 message（TimeoutException / CancellationException 等）时不能被判成
         * 「成功 + 进度 100」，否则半途中断的 run 会伪装成一份完整报告。
         * <p>
         * 用两条用例：第一条已经跑完并落明细，进度回写才炸 —— 终态进度必须是真实的 50，
         * 既不是 0（否定了已经花掉 token 的那条），也不是 100（谎报跑完）。
         */
        @Test
        void runWithoutErrorMessageShouldStillFailAndKeepRealProgress() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "两用例集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", "[\"doc1\"]"), evalCase("c2", "q2", "[\"doc2\"]")));
            when(hybridRetriever.retrieve(anyString(), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc1", "内容", 0.9)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            // 进度单列回写本身抛了异常：进 catch 的是一个 message 为 null 的 NPE
            doThrow(new NullPointerException()).when(service).update(any(Wrapper.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("failed");
            assertThat(run.getErrorMessage()).contains("NullPointerException");
            // 只跑完 1/2，进度不能伪装成 100，也不该抹掉已经完成的这一条
            assertThat(run.getProgress()).isEqualTo(50);
            assertThat(summaryOf(run)).containsEntry("completed", 1).containsEntry("totalCases", 2);
            verify(hybridRetriever, times(1)).retrieve(anyString(), any(RetrievalOptions.class));
            verify(resultService, times(1)).record(any(EvalResultEntity.class));
        }

        /** M2：明细全写失败 = 只有均值没有明细，跟「没跑 case」长得一样，必须当成失败 */
        @Test
        void runShouldFailWhenEveryDetailWriteFails() {
            stubSingleCaseRun();
            when(resultService.record(any(EvalResultEntity.class))).thenReturn(false);

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("failed");
            assertThat(run.getErrorMessage()).contains("明细落库失败");
            assertThat(summaryOf(run)).containsEntry("resultWriteFailures", 1)
                    .containsEntry("completed", 1);
        }

        /** 部分写失败仍出报告，但缺了哪几行得计下来供前端告警 */
        @Test
        void runShouldKeepCompletedAndCountPartialWriteFailures() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "两用例集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", null), evalCase("c2", "q2", null)));
            stubWrite(true);
            when(resultService.record(any(EvalResultEntity.class))).thenReturn(false, true);

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("completed");
            assertThat(summaryOf(run)).containsEntry("resultWriteFailures", 1)
                    .containsEntry("completed", 2);
            assertThat(run.getProgress()).isEqualTo(100);
        }

        /**
         * m1：没有任何可评指标（没参考文档、没知识库、没配判定模型）的用例是正常跑完，
         * 不能被整列标成「部分」把真异常淹在噪声里。
         */
        @Test
        void caseShouldBeSuccessWhenNothingIsScorable() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "裸集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(evalCase("c1", "q1", null)));
            stubWrite(true);

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(singleRecordedResult().getStatus()).isEqualTo("success");
            assertThat(run.getStatus()).isEqualTo("completed");
        }

        /** 取消后不再跑剩下 case；已经跑完的那条花了 token，明细照落，不能把 completed 空悬 */
        @Test
        void cancelShouldStopBeforeNextCase() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "两用例集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", "[\"doc1\"]"), evalCase("c2", "q2", "[\"doc2\"]")));
            doReturn(runningRun("r1")).when(service).getById("r1");
            stubWrite(true);
            when(hybridRetriever.retrieve(eq("q1"), any(RetrievalOptions.class))).thenAnswer(invocation -> {
                service.cancelRun("r1");
                return List.of(retrieval("doc1", "内容", 0.9));
            });

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            verify(hybridRetriever, times(1)).retrieve(anyString(), any(RetrievalOptions.class));
            assertThat(run.getStatus()).isEqualTo("cancelled");
            assertThat(run.getProgress()).isEqualTo(50);
            assertThat(summaryOf(run)).containsEntry("completed", 1).containsEntry("totalCases", 2);
            verify(resultService, times(1)).record(any(EvalResultEntity.class));
        }

        private void stubSingleCaseRun() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(evalCase("c1", "q1", "[\"doc1\"]")));
            when(hybridRetriever.retrieve(anyString(), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc1", "内容", 0.9)));
            stubWrite(true);
        }

        private void stubWrite(boolean updateResult) {
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(updateResult).when(service).update(any(Wrapper.class));
        }

        private static EvalRunEntity runningRun(String id) {
            EvalRunEntity run = new EvalRunEntity();
            run.setId(id);
            run.setStatus("running");
            return run;
        }
    }

    @Nested
    class ParallelExecution {

        /** 默认串行：并行是「要快再开」而不是默认开，小数据集下串行更好预测 */
        @Test
        void runShouldStaySequentialByDefault() {
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            stubOverlappingRetrieval(3, inFlight, maxInFlight);

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(maxInFlight.get()).isEqualTo(1);
            assertThat(summaryOf(run)).containsEntry("completed", 3);
        }

        /**
         * 并行度 >1 时 case 真并发跑，且共享累加器不丢数。
         * <p>
         * 并行下最先坏的往往不是报错而是「跑完了但均值少算了几条」：completed / 出分条数 /
         * 明细行数三个数必须一致。
         */
        @Test
        void runShouldExecuteCasesConcurrentlyWhenParallelismConfigured() {
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            stubOverlappingRetrieval(3, inFlight, maxInFlight);

            Map<String, Object> config = new HashMap<>();
            config.put("parallelism", 3);
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            assertThat(maxInFlight.get()).isGreaterThan(1);
            Map<String, Object> summary = summaryOf(run);
            assertThat(summary).containsEntry("completed", 3)
                    .containsEntry("failed", 0)
                    .containsEntry("recallCases", 3)
                    .containsEntry("avgRecall", "1.0000");
            assertThat(recordedResults()).hasSize(3);
            assertThat(run.getStatus()).isEqualTo("completed");
            assertThat(run.getProgress()).isEqualTo(100);
        }

        /**
         * 三条用例都走检索，检索里模拟一段重叠耗时，取「同时在飞的峰值」当并发度证据。
         */
        private void stubOverlappingRetrieval(int caseCount,
                                              AtomicInteger inFlight, AtomicInteger maxInFlight) {
            List<EvalCaseEntity> cases = new ArrayList<>();
            for (int i = 1; i <= caseCount; i++) {
                cases.add(evalCase("c" + i, "q" + i, "[\"doc1\"]"));
            }
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "并发集"));
            when(caseService.listByDataset("ds1")).thenReturn(cases);
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));
            when(hybridRetriever.retrieve(anyString(), any(RetrievalOptions.class))).thenAnswer(invocation -> {
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(40);
                } finally {
                    inFlight.decrementAndGet();
                }
                return List.of(retrieval("doc1", "内容", 0.9));
            });
        }
    }

    @Nested
    class MetricSpiWiring {

        /** 新指标只靠注册就能出现在明细 extra_scores 与汇总 metrics，主流程不随指标增减而改 */
        @Test
        void registeredMetricShouldLandInDetailAndSummary() {
            service = newService(new EvalMetricRegistry(List.of(
                    new StubMetric("faithfulness", (sample, context) -> MetricScore.of(0.75, 4)))));
            stubSingleMetricCase();

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            EvalResultEntity result = singleRecordedResult();
            assertThat(JsonUtils.parseMap(result.getExtraScores())).containsEntry("faithfulness", 0.75);
            // 召回与指标都出了分：没配模型不再是「缺分」的理由
            assertThat(result.getStatus()).isEqualTo("success");
            assertThat(JsonUtils.parseMap(result.getDetail())).containsKey("metrics");

            Map<String, Object> metrics = extraMetrics(run);
            assertThat(metrics).containsKeys("faithfulness");
            assertThat((Map<String, Object>) metrics.get("faithfulness"))
                    .containsEntry("avg", 0.75).containsEntry("cases", 1);
        }

        /**
         * 指标「本该出分却没出」（failed）时这条 case 记 partial：
         * 拿一个 0 分污染均值，与默默当成没这个指标，两者都会把优化方向带偏。
         */
        @Test
        void failedMetricShouldMarkCasePartialAndStayOutOfAverage() {
            service = newService(new EvalMetricRegistry(List.of(
                    new StubMetric("contextPrecision", (sample, context) -> MetricScore.failed("判定输出无法解析")))));
            stubSingleMetricCase();

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            EvalResultEntity result = singleRecordedResult();
            assertThat(result.getStatus()).isEqualTo("partial");
            assertThat(result.getExtraScores()).isNull();
            assertThat(extraMetrics(run)).isEmpty();
            assertThat(summaryOf(run)).containsEntry("completed", 1).containsEntry("failed", 0);
        }

        /** 结构性不适用（skipped）不该把整条 case 拖成 partial */
        @Test
        void skippedMetricShouldNotMarkCasePartial() {
            service = newService(new EvalMetricRegistry(List.of(
                    new StubMetric("contextRecall", (sample, context) -> MetricScore.skipped("用例没给片段级标注")))));
            stubSingleMetricCase();

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(singleRecordedResult().getStatus()).isEqualTo("success");
            assertThat(extraMetrics(run)).isEmpty();
        }

        /** config.metrics 显式点名时只跑点名的那些，拼错名字不应让整轮评估失败 */
        @Test
        void configMetricsShouldNarrowSelectedMetricsAndIgnoreUnknownNames() {
            AtomicInteger scored = new AtomicInteger();
            EvalMetric picked = new StubMetric("faithfulness",
                    (sample, context) -> {
                        scored.incrementAndGet();
                        return MetricScore.of(1.0);
                    });
            EvalMetric untouched = new StubMetric("answerRelevancy",
                    (sample, context) -> MetricScore.of(0.1));
            service = newService(new EvalMetricRegistry(List.of(picked, untouched)));
            stubSingleMetricCase();

            Map<String, Object> config = new HashMap<>();
            config.put("metrics", List.of("faithfulness", "notExists"));
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            assertThat(scored.get()).isEqualTo(1);
            assertThat(JsonUtils.parseMap(singleRecordedResult().getExtraScores()))
                    .containsOnlyKeys("faithfulness");
            assertThat(extraMetrics(run)).containsOnlyKeys("faithfulness");
        }

        private void stubSingleMetricCase() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "指标集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", "[\"doc1\"]")));
            when(hybridRetriever.retrieve(anyString(), any(RetrievalOptions.class)))
                    .thenReturn(List.of(retrieval("doc1", "内容", 0.9)));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));
            doReturn(true).when(service).update(any(Wrapper.class));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> extraMetrics(EvalRunEntity run) {
            return (Map<String, Object>) summaryOf(run).get("metrics");
        }

        /** 可控假指标：避开真模型调用，只验接线（record 组件名就是 name()，不需额外实装） */
        private record StubMetric(String name,
                                  BiFunction<EvalSample, MetricContext, MetricScore> action) implements EvalMetric {

            @Override
            public boolean supports(EvalSample sample, MetricContext context) {
                // 假指标不依回答：没配模型时也要能验通接线
                return true;
            }

            @Override
            public MetricScore score(EvalSample sample, MetricContext context) {
                return action.apply(sample, context);
            }
        }
    }

    @Nested
    class GetReport {

        /** 运行记录缺失时抛 ApiException */
        @Test
        void getReportShouldThrowWhenMissing() {
            doReturn(null).when(service).getById("r-missing");

            assertThatThrownBy(() -> service.getReport("r-missing"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("评估运行不存在");
        }

        /** 运行记录存在时原样返回 */
        @Test
        void getReportShouldReturnRun() {
            EvalRunEntity run = new EvalRunEntity();
            run.setId("r1");
            run.setStatus("completed");
            doReturn(run).when(service).getById("r1");

            assertThat(service.getReport("r1")).isSameAs(run);
        }
    }

    @Nested
    class RunCompare {

        private EvalRunEntity run(String id, String datasetId, String summary) {
            EvalRunEntity run = new EvalRunEntity();
            run.setId(id);
            run.setDatasetId(datasetId);
            run.setStatus("completed");
            run.setStartTime(LocalDateTime.of(2026, 10, 1, 10, 0));
            run.setSummary(summary);
            return run;
        }

        private EvalResultEntity row(String caseId, Double recall, Double correctness, String extraScores) {
            EvalResultEntity row = new EvalResultEntity();
            row.setCaseId(caseId);
            row.setQuestion("问题-" + caseId);
            row.setStatus("completed");
            row.setRecall(recall);
            row.setCorrectness(correctness);
            row.setExtraScores(extraScores);
            return row;
        }

        /** 均值为了控宽度存成了字符串，对比时要还原成数；SPI 指标嵌在 summary.metrics 一层 */
        private static final String FROM_SUMMARY = "{\"avgRecall\":\"0.8000\",\"recallCases\":10,"
                + "\"avgCorrectness\":\"0.7000\",\"correctnessCases\":8,"
                + "\"avgLatency\":1200,\"totalTokens\":5000,"
                + "\"metrics\":{\"faithfulness\":{\"avg\":0.5,\"cases\":4}}}";
        private static final String TO_SUMMARY = "{\"avgRecall\":\"0.7000\",\"recallCases\":10,"
                + "\"avgCorrectness\":\"0.7000\",\"correctnessCases\":8,"
                + "\"avgLatency\":2400,\"totalTokens\":5000,"
                + "\"metrics\":{\"faithfulness\":{\"avg\":0.6,\"cases\":20}}}";

        private void stubTwoRuns(String fromSummary, String toSummary) {
            doReturn(run("r0", "ds1", fromSummary)).when(service).getById("r0");
            doReturn(run("r1", "ds1", toSummary)).when(service).getById("r1");
        }

        @Test
        void shouldRejectBlankSameAndMissingRuns() {
            assertThatThrownBy(() -> service.compare(null, "r1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("请选择要对比的两个评估运行");
            assertThatThrownBy(() -> service.compare("r1", "  "))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.compare("r1", "r1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("同一个评估运行无需对比");

            doReturn(null).when(service).getById("r-missing");
            assertThatThrownBy(() -> service.compare("r-missing", "r1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("评估运行不存在: r-missing");
        }

        @Test
        void shouldReportMetricAndCostDeltas() {
            stubTwoRuns(FROM_SUMMARY, TO_SUMMARY);
            when(resultService.listByRun("r0")).thenReturn(List.of(
                    row("c1", 1.0, 1.0, "{\"faithfulness\":0.9}"),
                    row("c2", 0.2, 0.2, null)));
            when(resultService.listByRun("r1")).thenReturn(List.of(
                    row("c1", 0.5, 1.0, "{\"faithfulness\":0.4}"),
                    row("c2", 0.8, 0.8, null)));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            assertThat(compare.isSameDataset()).isTrue();
            // 字符串均值还原成数：0.8 → 0.7
            assertThat(compare.getMetrics().get("recall").getFrom()).isEqualTo(0.8);
            assertThat(compare.getMetrics().get("recall").getDelta()).isEqualTo(-0.1);
            assertThat(compare.getMetrics().get("recall").getFromCases()).isEqualTo(10);
            // 持平也算一项：0.7 → 0.7 的差值是 0，不是「没比」
            assertThat(compare.getMetrics().get("correctness").getDelta()).isZero();
            // SPI 指标两侧样本数差得远，均值必须带上分母才能读
            assertThat(compare.getMetrics().get("faithfulness").getDelta()).isEqualTo(0.1);
            assertThat(compare.getMetrics().get("faithfulness").getFromCases()).isEqualTo(4);
            assertThat(compare.getMetrics().get("faithfulness").getToCases()).isEqualTo(20);
            // 分数没跌但耗时翻倍：成本单独一组，不然这次退化看不见
            assertThat(compare.getCost().get("avgLatency").getDelta()).isEqualTo(1200.0);
            assertThat(compare.getCost().get("totalTokens").getDelta()).isZero();
        }

        @Test
        void shouldListWorstRegressionsFirstAndCountEveryCase() {
            stubTwoRuns(FROM_SUMMARY, TO_SUMMARY);
            when(resultService.listByRun("r0")).thenReturn(List.of(
                    row("c1", 1.0, null, null),
                    row("c2", 0.2, null, null),
                    row("c3", 0.5, null, null)));
            when(resultService.listByRun("r1")).thenReturn(List.of(
                    row("c1", 0.5, null, null),
                    row("c2", 0.8, null, null),
                    row("c4", 0.9, null, null)));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            // 跌得最狠的排最前；只出现在一侧的那两条没差值，落在末尾
            assertThat(compare.getCases()).extracting(EvalRunCompareDTO.CaseDiff::getCaseId)
                    .containsExactly("c1", "c2", "c3", "c4");
            assertThat(compare.getCases().get(0).getDelta()).isEqualTo(-0.5);
            assertThat(compare.getCases().get(2).getDelta()).isNull();
            // 问题取基准侧快照，基准侧没有就读对比侧
            assertThat(compare.getCases().get(2).getQuestion()).isEqualTo("问题-c3");
            assertThat(compare.getCases().get(3).getQuestion()).isEqualTo("问题-c4");
            assertThat(compare.getRegressed()).isEqualTo(1);
            assertThat(compare.getImproved()).isEqualTo(1);
            assertThat(compare.getOnlyInFrom()).isEqualTo(1);
            assertThat(compare.getOnlyInTo()).isEqualTo(1);
        }

        @Test
        void shouldNotCallTinyWobbleARegression() {
            stubTwoRuns("{}", "{}");
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.9, null, null)));
            // 0.9 → 0.90004：双精度末位的抖动不该被显示成一次涨跌
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.90004, null, null)));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            assertThat(compare.getRegressed()).isZero();
            assertThat(compare.getImproved()).isZero();
        }

        @Test
        void shouldLeaveDeltaNullWhenEitherSideHasNoScore() {
            stubTwoRuns("{\"avgRecall\":\"0.8000\"}", "{}");
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, null, null)));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", null, null, null)));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            // 把「本轮没跑这个指标」显示成「跌到零」会让人照着错的数去调参
            assertThat(compare.getMetrics().get("recall").getDelta()).isNull();
            assertThat(compare.getMetrics().get("recall").getTo()).isNull();
            assertThat(compare.getCases().get(0).getCompositeTo()).isNull();
            assertThat(compare.getCases().get(0).getDelta()).isNull();
            // 两侧都没读数的指标干脆不占一行
            assertThat(compare.getMetrics()).doesNotContainKey("correctness");
        }

        @Test
        void shouldSurviveDirtySummaryAndScores() {
            // 对比是只读接口：一行脏 JSON 不该把整个接口打成 500
            stubTwoRuns("not-json", "{\"avgRecall\":0.7}");
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, null, "{broken")));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.7, null, null)));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            // 脏 summary 按「那一侧没读数」处理：只有对比侧出了数，差值仍为空
            assertThat(compare.getMetrics()).containsOnlyKeys("recall");
            assertThat(compare.getMetrics().get("recall").getFrom()).isNull();
            assertThat(compare.getMetrics().get("recall").getTo()).isEqualTo(0.7);
            // 脏 extraScores 按「这些指标没跑」处理，不影响真列的差值
            assertThat(compare.getCases().get(0).getMetrics()).containsOnlyKeys("recall");
            assertThat(compare.getCases().get(0).getDelta()).isEqualTo(-0.1);
        }

        @Test
        void shouldMarkCrossDatasetCompareAsNotComparable() {
            doReturn(run("r0", "ds1", FROM_SUMMARY)).when(service).getById("r0");
            doReturn(run("r1", "ds2", TO_SUMMARY)).when(service).getById("r1");
            when(resultService.listByRun("r0")).thenReturn(List.of());
            when(resultService.listByRun("r1")).thenReturn(List.of());

            // 跨数据集时逐用例没意义（题目都不一样），只给一个「不可比」标记让前端明说
            assertThat(service.compare("r0", "r1").isSameDataset()).isFalse();
        }

        @Test
        void shouldKeepLastRowPerCaseAndTruncateTheDiffList() {
            stubTwoRuns("{}", "{}");
            // 同一条 case 多行（重跑）以最后一行为准：明细正序读出，后来者覆盖正是想要的口径
            when(resultService.listByRun("r0")).thenReturn(List.of(
                    row("c0", 0.4, null, null), row("c0", 0.6, null, null)));
            List<EvalResultEntity> toRows = new ArrayList<>();
            toRows.add(row("c0", 0.8, null, null));
            for (int i = 1; i < 201; i++) {
                toRows.add(row("c" + i, 0.5, null, null));
            }
            when(resultService.listByRun("r1")).thenReturn(toRows);

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            assertThat(compare.getCases().get(0).getCaseId()).isEqualTo("c0");
            // 0.6 → 0.8：用最后一行而不是第一行
            assertThat(compare.getCases().get(0).getCompositeFrom()).isEqualTo(0.6);
            // 回归清单是给人逐条看的，不是导数据；计数仍按全量
            assertThat(compare.getCases()).hasSize(200);
            assertThat(compare.getOnlyInTo()).isEqualTo(200);
        }

        /** 跌过阈值的指标进告警，持平与上涨的不进；成本翻倍不算分数回归 */
        @Test
        void shouldAlertMetricsThatFellPastTheThreshold() {
            stubTwoRuns(FROM_SUMMARY, TO_SUMMARY);
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, 0.7, "{\"faithfulness\":0.5}")));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.7, 0.7, "{\"faithfulness\":0.6}")));

            EvalRunCompareDTO compare = service.compare("r0", "r1");

            // recall 0.8 → 0.7：跌了 10 个百分点，超过默认 5% 的阈值
            assertThat(compare.getRegressThreshold()).isEqualTo(0.05);
            assertThat(compare.getRegressAlerts()).extracting(EvalRunCompareDTO.RegressAlert::getName)
                    .containsExactly("recall");
            assertThat(compare.getRegressAlerts().get(0).getDelta()).isEqualTo(-0.1);
            // correctness 持平、faithfulness 涨了；avgLatency 翻倍是钱的问题，塞进门禁等于每次涨价都得人工放行
            assertThat(compare.getMetrics()).containsKeys("correctness", "faithfulness");
        }

        /** 没跌过阈值就不该响：不然调阈值这件事本身就成了噪声源 */
        @Test
        void shouldNotAlertInsideTheThreshold() {
            stubTwoRuns("{\"avgRecall\":\"0.8000\",\"recallCases\":10}",
                    "{\"avgRecall\":\"0.7600\",\"recallCases\":10}");
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, null, null)));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.76, null, null)));

            assertThat(service.compare("r0", "r1").getRegressAlerts()).isEmpty();
        }

        /** 两条用例的均值跌穿阈值是噪声，拦下来只会让人去查错东西 */
        @Test
        void shouldNotAlertOnThinSamples() {
            stubTwoRuns("{\"avgRecall\":\"0.8000\",\"recallCases\":10,"
                            + "\"metrics\":{\"faithfulness\":{\"avg\":0.8,\"cases\":10}}}",
                    "{\"avgRecall\":\"0.6000\",\"recallCases\":2,"
                            + "\"metrics\":{\"faithfulness\":{\"avg\":0.4,\"cases\":2}}}");
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, null, "{\"faithfulness\":0.8}")));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.4, null, "{\"faithfulness\":0.4}")));

            assertThat(service.compare("r0", "r1").getRegressAlerts()).isEmpty();
        }

        /** 阈值配成 0 就是明确表态「不要门禁」：读数照给，但一项也不进告警 */
        @Test
        void shouldSilenceTheGateWhenThresholdIsNotPositive() {
            stubTwoRuns(FROM_SUMMARY, TO_SUMMARY);
            when(resultService.listByRun("r0")).thenReturn(List.of(row("c1", 0.8, null, null)));
            when(resultService.listByRun("r1")).thenReturn(List.of(row("c1", 0.7, null, null)));
            assertThat(service.compare("r0", "r1").getRegressAlerts()).hasSize(1);

            ReflectionTestUtils.setField(service, "regressThreshold", 0.0);
            assertThat(service.compare("r0", "r1").getRegressAlerts()).isEmpty();
        }
    }

    @Nested
    class PreviousRun {

        @SuppressWarnings("unchecked")
        private LambdaQueryWrapper<EvalRunEntity> capturePreviousWrapper() {
            ArgumentCaptor<Wrapper<EvalRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).getOne(captor.capture(), anyBoolean());
            LambdaQueryWrapper<EvalRunEntity> wrapper =
                    (LambdaQueryWrapper<EvalRunEntity>) captor.getValue();
            // 条件是延迟渲染的：不先跑一遍 getSqlSegment()，paramNameValuePairs 还是个空 map
            wrapper.getSqlSegment();
            return wrapper;
        }

        @Test
        void shouldPickLatestFinishedRunOfTheSameDataset() {
            EvalRunEntity current = new EvalRunEntity();
            current.setId("r1");
            current.setDatasetId("ds1");
            current.setStartTime(LocalDateTime.of(2026, 10, 9, 10, 0));
            doReturn(current).when(service).getById("r1");
            EvalRunEntity previous = new EvalRunEntity();
            previous.setId("r0");
            doReturn(previous).when(service).getOne(any(Wrapper.class), anyBoolean());

            assertThat(service.previousRun("r1")).isSameAs(previous);

            LambdaQueryWrapper<EvalRunEntity> wrapper = capturePreviousWrapper();
            assertThat(wrapper.getTargetSql()).contains("dataset_id", "status", "NOT IN", "start_time");
            assertThat(wrapper.getSqlSegment()).containsIgnoringCase("ORDER BY start_time DESC")
                    .contains("limit 1");
            // 只拿已结束的运行做基准，且必须排除自己
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains("ds1", "r1", LocalDateTime.of(2026, 10, 9, 10, 0));
        }

        @Test
        void shouldFallBackToCreateTimeWhenStartIsMissing() {
            // 刚开始就被取消的行没有 startTime：拿创建时间兜底，而不是报一个「找不到上一次」的假错
            EvalRunEntity current = new EvalRunEntity();
            current.setId("r1");
            current.setDatasetId("ds1");
            current.setCreateTime(LocalDateTime.of(2026, 10, 8, 18, 0));
            doReturn(current).when(service).getById("r1");
            doReturn(new EvalRunEntity()).when(service).getOne(any(Wrapper.class), anyBoolean());

            service.previousRun("r1");

            assertThat(capturePreviousWrapper().getParamNameValuePairs().values())
                    .contains(LocalDateTime.of(2026, 10, 8, 18, 0));
        }

        @Test
        void shouldThrowWhenItIsTheFirstRun() {
            EvalRunEntity current = new EvalRunEntity();
            current.setId("r1");
            current.setDatasetId("ds1");
            current.setStartTime(LocalDateTime.of(2026, 10, 9, 10, 0));
            doReturn(current).when(service).getById("r1");
            doReturn(null).when(service).getOne(any(Wrapper.class), anyBoolean());

            // 不拿不相干（跳数据集 / 还在跑）的运行来凑：比出来的差值混的是数据而不是改动
            assertThatThrownBy(() -> service.previousRun("r1"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("没有可对比的上一次运行");
        }
    }
}
