package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
    private OpenAICompatibleClient modelClient;

    private EvalRunServiceImpl service;
    private AtomicReference<Runnable> pendingTask;

    @BeforeEach
    void setUp() {
        pendingTask = new AtomicReference<>();
        service = spy(new EvalRunServiceImpl(datasetService, caseService, hybridRetriever,
                modelService, pendingTask::set));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCaseResult(EvalRunEntity run) {
        List<Map<String, Object>> results = JsonUtils.parseListOfMap(run.getResults());
        assertThat(results).hasSize(1);
        return results.get(0);
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

        /** 启动时应先落库 pending 快照，异步执行完成后回填汇总与逐 case 结果 */
        @Test
        void runShouldPersistPendingThenCompleteWithSummaryAndRecall() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "什么是仓颉？", "[\"doc1\",\"doc2\"]")));
            when(hybridRetriever.retrieve(eq("什么是仓颉？"), eq(List.of("kb1")), eq(3)))
                    .thenReturn(List.of(retrieval("doc1", "仓颉是国产编程语言", 0.92),
                            retrieval("doc3", "无关内容", 0.30)));
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

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

            Map<String, Object> summary = JsonUtils.parseMap(run.getSummary());
            assertThat(summary).containsEntry("totalCases", 1)
                    .containsEntry("completed", 1)
                    .containsEntry("totalTokens", 0);
            assertThat(summary.get("avgRecall")).isEqualTo("0.5000");

            Map<String, Object> caseResult = firstCaseResult(run);
            assertThat(caseResult.get("caseId")).isEqualTo("c1");
            assertThat(caseResult.get("question")).isEqualTo("什么是仓颉？");
            assertThat(caseResult.get("recall")).isEqualTo("0.5000");
            assertThat(caseResult).doesNotContainKey("answer");

            List<Map<String, Object>> sources =
                    (List<Map<String, Object>>) caseResult.get("retrievalSources");
            assertThat(sources).hasSize(2);
            assertThat(sources.get(0)).containsEntry("documentId", "doc1")
                    .containsEntry("content", "仓颉是国产编程语言");
            assertThat(sources.get(0).get("score")).isEqualTo(0.92);

            // start + 逐 case 进度 + 最终汇总
            verify(service, times(3)).updateById(run);
        }

        /** topK 非 Integer 或缺失时使用默认值 5 */
        @Test
        void runShouldFallbackToDefaultTopKWhenConfigInvalid() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(hybridRetriever.retrieve(eq("q1"), eq(List.of("kb1")), eq(5)))
                    .thenReturn(List.of(retrieval("doc9", "内容", 0.5)));
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            Map<String, Object> config = new HashMap<>();
            config.put("topK", "3");
            service.run("ds1", config);
            executePendingTask();

            verify(hybridRetriever).retrieve("q1", List.of("kb1"), 5);
        }

        /** 数据集未绑定知识库时跳过检索，召回率按 0 记录 */
        @Test
        void runShouldSkipRetrievalWhenDatasetHasNoKnowledgeBase() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "无知识库集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", "[\"doc1\"]")));
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            verify(hybridRetriever, never()).retrieve(anyString(), anyList(), anyInt());

            Map<String, Object> caseResult = firstCaseResult(run);
            assertThat(caseResult.get("recall")).isEqualTo("0.0000");
            assertThat((List<?>) caseResult.get("retrievalSources")).isEmpty();
            assertThat(JsonUtils.parseMap(run.getSummary()).get("avgRecall")).isEqualTo("0.0000");
        }

        /** 多用例时进度按整数百分比推进：33 / 66 / 100 */
        @Test
        void runShouldUpdateProgressPerCase() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", null, "三用例集"));
            when(caseService.listByDataset("ds1")).thenReturn(List.of(
                    evalCase("c1", "q1", null), evalCase("c2", "q2", null), evalCase("c3", "q3", null)));
            doReturn(true).when(service).save(any(EvalRunEntity.class));

            // updateById 传入的是同一个可变 run 实例，需在调用时刻快照进度与状态
            List<Integer> progressSnapshots = new ArrayList<>();
            List<String> statusSnapshots = new ArrayList<>();
            List<LocalDateTime> startTimeSnapshots = new ArrayList<>();
            doAnswer(invocation -> {
                EvalRunEntity updated = invocation.getArgument(0);
                progressSnapshots.add(updated.getProgress());
                statusSnapshots.add(updated.getStatus());
                startTimeSnapshots.add(updated.getStartTime());
                return true;
            }).when(service).updateById(any(EvalRunEntity.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            assertThat(progressSnapshots).containsExactly(0, 33, 66, 100, 100);
            assertThat(statusSnapshots)
                    .containsExactly("running", "running", "running", "running", "completed");
            assertThat(startTimeSnapshots.get(0)).isNotNull();
            assertThat(run.getEndTime()).isAfterOrEqualTo(run.getStartTime());
        }
    }

    @Nested
    class RunWithLlm {

        /** 配置 modelId 时生成回答并做 LLM-as-judge，汇总 token 与正确率 */
        @Test
        void runShouldGenerateAnswerAndJudgeScore() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "什么是仓颉？", null)));
            when(hybridRetriever.retrieve(eq("什么是仓颉？"), eq(List.of("kb1")), eq(5)))
                    .thenReturn(List.of(retrieval("doc1", "仓颉是国产编程语言", 0.9)));
            when(modelService.getClient("m1")).thenReturn(modelClient);
            when(modelClient.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("仓颉是华为推出的编程语言").totalTokens(12).build())
                    .thenReturn(ChatResponse.builder().content("0.85").build());
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            Map<String, Object> summary = JsonUtils.parseMap(run.getSummary());
            assertThat(summary).containsEntry("totalTokens", 12);
            assertThat(summary.get("avgCorrectness")).isEqualTo("0.85");

            Map<String, Object> caseResult = firstCaseResult(run);
            assertThat(caseResult.get("answer")).isEqualTo("仓颉是华为推出的编程语言");
            assertThat(caseResult.get("correctness")).isEqualTo("0.85");
            assertThat(caseResult).doesNotContainKey("error");

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
        }

        /** 评分输出无法解析为数字时给中性分 0.5 */
        @Test
        void runShouldFallbackToNeutralScoreWhenJudgeOutputUnparseable() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(modelService.getClient("m1")).thenReturn(modelClient);
            when(modelClient.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().content("答案").totalTokens(7).build())
                    .thenReturn(ChatResponse.builder().content("无法评分").build());
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            assertThat(firstCaseResult(run).get("correctness")).isEqualTo("0.50");
            assertThat(JsonUtils.parseMap(run.getSummary()).get("avgCorrectness")).isEqualTo("0.50");
        }

        /** 模型调用抛异常时该用例标记评估失败，整体仍完成且不计正确率 */
        @Test
        void runShouldMarkCaseFailedWhenLlmThrows() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null)));
            when(modelService.getClient("m1")).thenThrow(new RuntimeException("模型不可用"));
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            Map<String, Object> config = new HashMap<>();
            config.put("modelId", "m1");
            EvalRunEntity run = service.run("ds1", config);
            executePendingTask();

            assertThat(run.getStatus()).isEqualTo("completed");
            Map<String, Object> caseResult = firstCaseResult(run);
            assertThat(caseResult.get("answer")).isEqualTo("评估失败: 模型不可用");
            assertThat(caseResult.get("correctness")).isEqualTo("0.00");

            Map<String, Object> summary = JsonUtils.parseMap(run.getSummary());
            assertThat(summary).doesNotContainKey("avgCorrectness");
            assertThat(summary).containsEntry("totalTokens", 0);
        }
    }

    @Nested
    class RunResilience {

        /** 检索异常被用例级捕获：记录 error 并继续执行后续用例 */
        @Test
        void runShouldRecordErrorWhenRetrievalThrows() {
            when(datasetService.getById("ds1")).thenReturn(dataset("ds1", "kb1", "回归集"));
            when(caseService.listByDataset("ds1"))
                    .thenReturn(List.of(evalCase("c1", "q1", null), evalCase("c2", "q2", null)));
            when(hybridRetriever.retrieve(eq("q1"), anyList(), anyInt()))
                    .thenThrow(new RuntimeException("向量库超时"));
            when(hybridRetriever.retrieve(eq("q2"), anyList(), anyInt()))
                    .thenReturn(List.of(retrieval("doc2", "内容", 0.7)));
            doReturn(true).when(service).save(any(EvalRunEntity.class));
            doReturn(true).when(service).updateById(any(EvalRunEntity.class));

            EvalRunEntity run = service.run("ds1", new HashMap<>());
            executePendingTask();

            List<Map<String, Object>> results = JsonUtils.parseListOfMap(run.getResults());
            assertThat(results).hasSize(2);
            assertThat(results.get(0).get("error")).isEqualTo("向量库超时");
            assertThat(results.get(0)).doesNotContainKey("recall");
            assertThat(results.get(1).get("recall")).isEqualTo("0.0000");
            assertThat(run.getStatus()).isEqualTo("completed");
            assertThat(JsonUtils.parseMap(run.getSummary())).containsEntry("completed", 2);
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
}
