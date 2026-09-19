package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.common.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.rag.HybridRetriever;
import cn.cangjiecloud.core.rag.RetrievalResult;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.entity.EvalRunEntity;
import cn.cangjiecloud.eval.mapper.EvalRunMapper;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import cn.cangjiecloud.eval.service.IEvalRunService;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 评估运行服务
 * <p>
 * 核心流程：逐 case 执行检索 + LLM 回答 + LLM-as-judge 评分，汇总指标。
 * 使用 businessExecutor 异步执行，通过 progress 字段追踪进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalRunServiceImpl extends ServiceImpl<EvalRunMapper, EvalRunEntity>
        implements IEvalRunService {

    private final IEvalDatasetService datasetService;
    private final IEvalCaseService caseService;
    private final HybridRetriever hybridRetriever;
    private final IModelService modelService;

    @Qualifier("businessExecutor")
    private final Executor businessExecutor;

    private static final int DEFAULT_TOP_K = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalRunEntity run(String datasetId, Map<String, Object> config) {
        EvalDatasetEntity dataset = datasetService.getById(datasetId);
        if (dataset == null) {
            throw new ApiException("数据集不存在: " + datasetId);
        }

        List<EvalCaseEntity> cases = caseService.listByDataset(datasetId);
        if (cases.isEmpty()) {
            throw new ApiException("数据集没有评估用例");
        }

        // 创建运行记录
        EvalRunEntity run = new EvalRunEntity();
        run.setDatasetId(datasetId);
        run.setConfigSnapshot(JsonUtils.toJSONString(config));
        run.setStatus("pending");
        run.setProgress(0);
        save(run);

        // 异步执行评估
        businessExecutor.execute(() -> executeRun(run, dataset, cases, config));

        log.info("评估运行已启动: runId={}, dataset={}, cases={}", run.getId(), dataset.getName(), cases.size());
        return run;
    }

    private void executeRun(EvalRunEntity run, EvalDatasetEntity dataset,
                            List<EvalCaseEntity> cases, Map<String, Object> config) {
        run.setStatus("running");
        run.setStartTime(LocalDateTime.now());
        updateById(run);

        List<String> kbIds = dataset.getKnowledgeBaseId() != null
                ? List.of(dataset.getKnowledgeBaseId()) : List.of();
        String modelId = (String) config.getOrDefault("modelId", null);
        int topK = config.get("topK") instanceof Integer ? (int) config.get("topK") : DEFAULT_TOP_K;

        List<Map<String, Object>> caseResults = new ArrayList<>();
        double totalRecall = 0;
        double totalCorrectness = 0;
        long totalLatency = 0;
        long totalTokens = 0;
        int completed = 0;

        for (int i = 0; i < cases.size(); i++) {
            EvalCaseEntity evalCase = cases.get(i);
            long caseStart = System.currentTimeMillis();
            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("caseId", evalCase.getId());
            caseResult.put("question", evalCase.getQuestion());

            try {
                // 1. 检索
                List<RetrievalResult> retrievalResults = kbIds.isEmpty()
                        ? List.of()
                        : hybridRetriever.retrieve(evalCase.getQuestion(), kbIds, topK);

                List<Map<String, Object>> sources = retrievalResults.stream()
                        .map(r -> {
                            Map<String, Object> s = new LinkedHashMap<>();
                            s.put("documentId", r.getDocumentId());
                            s.put("content", r.getContent());
                            s.put("score", r.getFinalScore());
                            return s;
                        }).toList();
                caseResult.put("retrievalSources", sources);

                // 2. 召回率计算
                double recall = calcRecall(retrievalResults, evalCase.getReferenceDocs());
                caseResult.put("recall", String.format("%.4f", recall));
                totalRecall += recall;

                // 3. LLM 回答 + LLM-as-judge
                if (StringUtils.hasText(modelId)) {
                    try {
                        OpenAICompatibleClient client = modelService.getClient(modelId);
                        ChatResponse answerResp = generateAnswer(client, evalCase, retrievalResults);
                        caseResult.put("answer", answerResp.getContent());
                        totalTokens += Math.max(answerResp.getTotalTokens(), 0);

                        double correctness = judgeAnswer(client, evalCase.getQuestion(),
                                evalCase.getExpectedAnswer(), answerResp.getContent());
                        caseResult.put("correctness", String.format("%.2f", correctness));
                        totalCorrectness += correctness;
                    } catch (Exception e) {
                        log.warn("LLM 评估失败: caseId={}, {}", evalCase.getId(), e.getMessage());
                        caseResult.put("answer", "评估失败: " + e.getMessage());
                        caseResult.put("correctness", "0.00");
                    }
                }

            } catch (Exception e) {
                log.error("用例评估异常: caseId={}", evalCase.getId(), e);
                caseResult.put("error", e.getMessage());
            }

            long latency = System.currentTimeMillis() - caseStart;
            caseResult.put("latency", latency);
            totalLatency += latency;
            caseResults.add(caseResult);
            completed++;

            // 更新进度
            run.setProgress((int) (completed * 100.0 / cases.size()));
            updateById(run);
        }

        // 汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCases", cases.size());
        summary.put("completed", completed);
        summary.put("avgRecall", String.format("%.4f", completed > 0 ? totalRecall / completed : 0));
        summary.put("totalLatency", totalLatency);
        summary.put("avgLatency", completed > 0 ? totalLatency / completed : 0);

        if (totalCorrectness > 0) {
            summary.put("avgCorrectness", String.format("%.2f", totalCorrectness / completed));
        }
        summary.put("totalTokens", totalTokens);

        run.setStatus("completed");
        run.setEndTime(LocalDateTime.now());
        run.setSummary(JsonUtils.toJSONString(summary));
        run.setResults(JsonUtils.toJSONString(caseResults));
        run.setProgress(100);
        updateById(run);

        log.info("评估运行完成: runId={}, recall={}, correctness={}",
                run.getId(), summary.get("avgRecall"), summary.getOrDefault("avgCorrectness", "N/A"));
    }

    /**
     * 计算 recall@k
     */
    @SuppressWarnings("unchecked")
    private double calcRecall(List<RetrievalResult> results, String referenceDocsJson) {
        if (!StringUtils.hasText(referenceDocsJson) || results.isEmpty()) {
            return 0.0;
        }
        try {
            Set<String> referenceIds = new HashSet<>();
            JsonNode arr = JsonUtils.parseArray(referenceDocsJson);
            if (arr != null) {
                for (JsonNode o : arr) {
                    referenceIds.add(o.isTextual() ? o.asText() : o.toString());
                }
            }
            if (referenceIds.isEmpty()) return 0.0;

            Set<String> retrievedIds = results.stream()
                    .map(RetrievalResult::getDocumentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            long intersectCount = retrievedIds.stream().filter(referenceIds::contains).count();
            return (double) intersectCount / referenceIds.size();
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * LLM 生成回答（基于检索上下文）
     */
    private ChatResponse generateAnswer(OpenAICompatibleClient client, EvalCaseEntity evalCase,
                                        List<RetrievalResult> retrievalResults) {
        StringBuilder context = new StringBuilder();
        if (!retrievalResults.isEmpty()) {
            context.append("参考资料：\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                context.append("[").append(i + 1).append("] ")
                        .append(retrievalResults.get(i).getContent()).append("\n");
            }
        }

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("你是一个问答助手。请根据参考资料回答问题。如果参考资料没有相关信息，请如实说明。"),
                        ChatMessage.user(context + "\n问题：" + evalCase.getQuestion())
                ))
                .temperature(0.1)
                .build();
        return client.chat(request);
    }

    /**
     * LLM-as-judge：对比生成回答与期望回答
     */
    private double judgeAnswer(OpenAICompatibleClient client, String question,
                                String expectedAnswer, String generatedAnswer) {
        if (!StringUtils.hasText(expectedAnswer) || !StringUtils.hasText(generatedAnswer)) {
            return 0.0;
        }

        String prompt = String.format(
                "请评估以下回答的正确性，给出 0-1 之间的分数（0=完全不正确，1=完全正确）。仅返回数字分数。\n\n"
                        + "问题：%s\n\n期望回答：%s\n\n生成回答：%s",
                question, expectedAnswer, generatedAnswer);

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("你是一个严格的评分者。仅返回一个 0 到 1 之间的数字分数，不要返回其他任何内容。"),
                        ChatMessage.user(prompt)
                ))
                .temperature(0.0)
                .build();
        ChatResponse response = client.chat(request);
        try {
            String content = response.getContent().trim();
            return Double.parseDouble(content.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0.5; // 解析失败给中性分
        }
    }

    @Override
    public EvalRunEntity getReport(String runId) {
        EvalRunEntity run = getById(runId);
        if (run == null) {
            throw new ApiException("评估运行不存在: " + runId);
        }
        return run;
    }
}