package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.core.eval.ContextChunk;
import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上下文召回（context recall，片段口径）：标注的参考片段有多少真被召回到。
 * <p>
 * 与已有的 {@code eval_result.recall}（文档口径）互补而不是替代：文档命中但片段没命中
 * 时文档口径给满分，会把 chunk 切分尺寸、topK 太小这类问题全掩盖掉 ——
 * 而「文档找到了、答题要的那一段没进上下文」恰恰是 RAG 最常见的失败形状。
 * <p>
 * 只吃片段级标注（{@code eval_case.reference_chunks}）：没有片段标注时直接跳过，
 * 拿文档级标注冒充片段口径算出来的分数与文档口径同名不同义，等于把同一件事记两遍。
 * 纯集合运算，不调模型。
 */
@Component
public class ContextRecallMetric implements EvalMetric {

    @Override
    public String name() {
        return "contextRecall";
    }

    @Override
    public String displayName() {
        return "上下文召回(片段)";
    }

    @Override
    public boolean requiresGroundTruth() {
        return true;
    }

    @Override
    public boolean supports(EvalSample sample, MetricContext context) {
        return sample != null && !sample.referenceChunks().isEmpty();
    }

    @Override
    public MetricScore score(EvalSample sample, MetricContext context) {
        if (!sample.hasContexts()) {
            // 拿空召回算出的 0 分是伪造读数（可能是数据集没绑知识库），不记
            return MetricScore.skipped("本次未执行检索或没有召回任何片段");
        }

        Set<String> expected = new LinkedHashSet<>(sample.referenceChunks());
        Set<String> retrieved = sample.contexts().stream()
                .map(ContextChunk::paragraphId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (retrieved.isEmpty()) {
            // 召回的片段全没带 paragraphId，无法与片段标注对齐（旧数据或在线回放才会这样）
            return MetricScore.skipped("召回结果未携带片段 ID，无法按片段口径比对");
        }

        Set<String> hit = new LinkedHashSet<>(expected);
        hit.retainAll(retrieved);
        Set<String> missed = new LinkedHashSet<>(expected);
        missed.removeAll(retrieved);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("referenceChunks", List.copyOf(expected));
        detail.put("hitChunks", List.copyOf(hit));
        detail.put("missedChunks", List.copyOf(missed));
        double score = (double) hit.size() / expected.size();
        return MetricScore.of(score, expected.size(), detail);
    }
}
