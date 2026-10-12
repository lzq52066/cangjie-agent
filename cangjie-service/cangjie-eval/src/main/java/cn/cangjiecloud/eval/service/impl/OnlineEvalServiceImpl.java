package cn.cangjiecloud.eval.service.impl;

import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.eval.EmbeddingFunction;
import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.JudgeFunction;
import cn.cangjiecloud.core.eval.JudgeResult;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import cn.cangjiecloud.core.eval.OnlineTraceSample;
import cn.cangjiecloud.core.eval.OnlineTraceSource;
import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.ChatRequest;
import cn.cangjiecloud.core.model.ChatResponse;
import cn.cangjiecloud.core.model.ChatTraceContext;
import cn.cangjiecloud.core.rag.EmbeddingProvider;
import cn.cangjiecloud.eval.dto.OnlineEvalRequestDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalSummaryDTO;
import cn.cangjiecloud.eval.metric.EvalMetricRegistry;
import cn.cangjiecloud.eval.service.IOnlineEvalService;
import cn.cangjiecloud.model.entity.ModelEntity;
import cn.cangjiecloud.model.provider.OpenAICompatibleClient;
import cn.cangjiecloud.model.service.IModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在线评测实现：捞样本 → 复用离线那套指标 SPI 打分 → 分数就地写回调用记录。
 * <p>
 * 指标与判定链路完全复用离线评测（同一个 {@link EvalMetricRegistry}、同一个 {@link MetricContext}），
 * 在线与离线因此可比：同一批指标、同一个判定口径，只是一个跑数据集、一个跑真实流量。
 * 在线样本没有标注，凡是要求 ground truth 的指标会自己跳过，报告里表现为「这个指标没分」，
 * 而不是拿一个假分数把水位算歪。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineEvalServiceImpl implements IOnlineEvalService {

    /** 手动触发时没给条数就评这么多：一次判定要跑好几轮模型调用，默认值就得替用户看住钱 */
    private static final int DEFAULT_LIMIT = 20;

    /** 单轮上限，与样本来源侧的夹取一致 */
    private static final int MAX_LIMIT = 200;

    /**
     * 判定调用的 traceId 前缀。
     * <p>
     * 必须以 {@code eval-} 开头：判定本身也是一次模型调用，也会被写进 llm_trace；
     * 不给它一个可识别的归属，下一轮抽样就会把判定调用当成线上问答再评一遍，越评越多。
     */
    private static final String TRACE_PREFIX = "eval-online-";

    /** 指标没自报系统提示时的默认值，与离线评测同一口径 */
    private static final String METRIC_SYSTEM = "你是严格的评测器。只输出符合要求的 JSON，不要输出任何多余文本。";

    private final EvalMetricRegistry metricRegistry;
    private final IModelService modelService;

    /**
     * 线上样本来源（由可观测性模块实现）：本模块不依赖可观测性模块，编译期只认 core 接口。
     * <p>
     * 没挂上时在线评测整体不可用，而不是返回一个空摘要 —— 空摘要看起来像「线上没有问题」，
     * 实际是「压根没评」。
     */
    @Autowired(required = false)
    private OnlineTraceSource traceSource;

    /** 向量化能力（answerRelevancy 用），缺失时该指标自行跳过 */
    @Autowired(required = false)
    private EmbeddingProvider embeddingProvider;

    /** 定时抽样开关：默认关 —— 开着它就等于默认同意持续花钱，必须由部署方显式打开 */
    @Value("${cangjie.eval.online.enabled:false}")
    private boolean scheduleEnabled;

    /** 定时轮每批条数 */
    @Value("${cangjie.eval.online.limit:20}")
    private int scheduledLimit;

    /**
     * 定时抽样。
     * <p>
     * 关掉开关时直接返回，不抛异常：一个没启用的后台任务不该把调度线程刷成错误日志来源。
     */
    @Scheduled(cron = "${cangjie.eval.online.cron:0 30 3 * * ?}")
    public void scheduledScore() {
        if (!scheduleEnabled) {
            return;
        }
        try {
            OnlineEvalRequestDTO request = new OnlineEvalRequestDTO();
            request.setLimit(scheduledLimit);
            OnlineEvalSummaryDTO summary = score(request);
            log.info("定时在线评测完成: 取样={}, 回填={}, 无分={}, 判定token={}",
                    summary.getSampled(), summary.getScored(), summary.getWithoutScore(),
                    summary.getJudgeTokens());
        } catch (Exception e) {
            // 定时任务里抛出去只会终止后续调度，这里按一轮失败记录，下一轮照常
            log.warn("定时在线评测失败: {}", e.toString());
        }
    }

    @Override
    public long pendingCount() {
        return traceSource == null ? 0L : traceSource.countUnscored();
    }

    @Override
    public OnlineEvalSummaryDTO score(OnlineEvalRequestDTO request) {
        OnlineTraceSource source = traceSource;
        if (source == null) {
            throw new ApiException("在线评测不可用：未接入模型调用记录来源");
        }
        OnlineEvalRequestDTO safe = request == null ? new OnlineEvalRequestDTO() : request;
        int limit = safe.getLimit() == null ? DEFAULT_LIMIT : Math.min(Math.max(safe.getLimit(), 1), MAX_LIMIT);
        long pendingBefore = source.countUnscored();

        List<OnlineTraceSample> samples = source.fetchUnscored(limit);
        List<EvalMetric> metrics = metricRegistry.resolve(safe.getMetrics());
        if (samples.isEmpty() || metrics.isEmpty()) {
            // 一个指标都没解析出来（名字全拼错）时也早退而不是硬跑：没有指标就没有判定调用，
            // 一趟空转只会把样本标成「评过但没分」
            return OnlineEvalSummaryDTO.builder()
                    .pendingBefore(pendingBefore)
                    .sampled(samples.size())
                    .scored(0)
                    .withoutScore(0)
                    .lostRace(0)
                    .judgeTokens(0L)
                    .metrics(new LinkedHashMap<>())
                    .build();
        }

        // 判定模型先取一次客户端再开跑：等到第一条样本才炸的话，剩下的样本会被逐条标成「评过但没分」，
        // 一批没评的样本就此被配置错误消耗掉
        JudgeClient judgeClient = resolveJudge(safe.getJudgeModelId());
        MetricContext context = metricContext(judgeClient);
        Map<String, double[]> aggregates = new LinkedHashMap<>();
        int scored = 0;
        int withoutScore = 0;
        int lostRace = 0;
        long judgeTokens = 0L;

        for (OnlineTraceSample sample : samples) {
            if (!sample.scoreable()) {
                // 正文没落库（关了存储或被截断）的样本评也评不出东西，但仍要标已评：
                // 不然每一轮取到的都是最新这几十条，积压永远推不动
                if (source.markScored(sample.id(), Map.of())) {
                    withoutScore++;
                } else {
                    lostRace++;
                }
                continue;
            }
            Map<String, MetricScore> scores = metricRegistry.run(sample.toEvalSample(), context, metrics);

            Map<String, Double> evaluated = new LinkedHashMap<>();
            scores.forEach((name, score) -> {
                if (score.evaluated()) {
                    evaluated.put(name, score.value());
                }
            });
            if (!source.markScored(sample.id(), evaluated)) {
                // 被别的执行抢先标注：本轮这些分数不入库，也不计入均值，免得同一个样本被算两次
                lostRace++;
                continue;
            }
            scored++;
            if (evaluated.isEmpty()) {
                withoutScore++;
            }
            evaluated.forEach((name, value) -> {
                double[] slot = aggregates.computeIfAbsent(name, key -> new double[2]);
                slot[0] += value;
                slot[1]++;
            });
        }
        judgeTokens = judgeClient.tokens.get();

        Map<String, OnlineEvalSummaryDTO.MetricAvg> averages = new LinkedHashMap<>();
        aggregates.forEach((name, slot) -> averages.put(name, new OnlineEvalSummaryDTO.MetricAvg(
                slot[1] == 0 ? null : Math.round(slot[0] / slot[1] * 10000.0) / 10000.0, (int) slot[1])));

        return OnlineEvalSummaryDTO.builder()
                .pendingBefore(pendingBefore)
                .sampled(samples.size())
                .scored(scored)
                .withoutScore(withoutScore)
                .lostRace(lostRace)
                .judgeTokens(judgeTokens)
                .metrics(averages)
                .build();
    }

    private JudgeClient resolveJudge(String judgeModelId) {
        String modelId = StringUtils.hasText(judgeModelId) ? judgeModelId.trim() : null;
        if (modelId == null) {
            ModelEntity defaultModel = modelService.getDefaultModel();
            if (defaultModel == null) {
                throw new ApiException("未配置默认模型，且本次未指定判定模型，无法在线评测");
            }
            modelId = defaultModel.getId();
        }
        return new JudgeClient(modelService.getClient(modelId), modelId);
    }

    /**
     * 本轮样本共用的能力上下文。
     * <p>
     * 判定客户端按本轮取一次而不是每条取一次：客户端里有连接池与熔断状态，
     * 每条重建既慢又会让熔断统计碎成一地。没标注的线上样本只能靠判定模型出分，
     * 因此这里无条件挂上 judge，不做「看指标要不要」的推定。
     */
    private MetricContext metricContext(JudgeClient judgeClient) {
        JudgeFunction judge = (system, prompt) -> {
            ChatResponse response = judgeClient.client.chat(ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(StringUtils.hasText(system) ? system : METRIC_SYSTEM),
                            ChatMessage.user(prompt)))
                    .temperature(0.0)
                    .traceContext(ChatTraceContext.builder()
                            .traceId(TRACE_PREFIX + judgeClient.modelId)
                            .requestId(TRACE_PREFIX + System.nanoTime())
                            .modelId(judgeClient.modelId)
                            .build())
                    .build());
            long input = Math.max(response.getPromptTokens(), 0);
            long output = Math.max(response.getCompletionTokens(), 0);
            judgeClient.tokens.addAndGet(input + output);
            return new JudgeResult(response.getContent(), input, output);
        };
        EmbeddingFunction embedding = embeddingProvider == null
                ? null : texts -> embeddingProvider.embedBatch(texts);
        return new MetricContext(judge, embedding, Map.of());
    }

    /** 本轮判定用的客户端与其模型 ID */
    private static final class JudgeClient {
        private final OpenAICompatibleClient client;
        private final String modelId;
        private final AtomicLong tokens = new AtomicLong();

        JudgeClient(OpenAICompatibleClient client, String modelId) {
            this.client = client;
            this.modelId = modelId;
        }
    }
}
