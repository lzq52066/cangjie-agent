package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.JudgeResult;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文精度（context precision）：召回的片段里有多少是真有用的，并且有用的排得够不够前。
 * <p>
 * 和现有的 {@code eval_result.recall} 不是一回事：recall 只看「该找到的找没找到」，
 * 不管位置；本指标按名次加权（precision@k 均值），一条能答题的片段被排到第 10 位、
 * 前面 9 条是噪声，recall 满分而这里会掉下来 —— 那正是「上下文塞满噪声导致回答跑偏」的信号，
 * 也是调 topK / 重排时真正要看的数。
 * <p>
 * 判定用 LLM 逐片段判断「能否用于回答这个问题」，一次调用判完全部片段。
 * 没落回判定的片段按不相关计（保守），分母口径与片段总数一致。
 */
@Component
public class ContextPrecisionMetric implements EvalMetric {

    private static final String PROMPT = """
            逐条判断每个「参考资料片段」能否用于回答这个问题：能提供任何一部分有效依据记 relevant=true，
            完全无关、只是同主题的水记 relevant=false。不要考虑片段之间的相对好坏，只看单条有没有用。
            只输出 JSON：{"verdicts": [{"index": 片段序号(从 1 开始), "relevant": true 或 false}]}
            每个片段都要给出一条结果，序号不要漏、不要重复。

            问题：%s

            参考资料片段：
            %s
            """;

    @Override
    public String name() {
        return "contextPrecision";
    }

    @Override
    public String displayName() {
        return "上下文精度";
    }

    @Override
    public boolean supports(EvalSample sample, MetricContext context) {
        return sample != null && sample.query() != null && !sample.query().isBlank()
                && sample.hasContexts() && context != null && context.hasJudge();
    }

    @Override
    public MetricScore score(EvalSample sample, MetricContext context) {
        List<String> contexts = sample.contextTexts();
        Map<Integer, Boolean> verdicts = judge(context, sample.query(), contexts);
        if (verdicts.isEmpty()) {
            return MetricScore.failed("判定输出无法与片段对齐");
        }

        // AP@k = Σ (前 i 条里的相关数 / i) × [第 i 条相关] ÷ 相关片段总数：只有命中时累计，
        // 相关的排得越靠前，前缀精度越高，分数越高
        double accumulated = 0.0;
        int relevant = 0;
        int unjudged = 0;
        for (int i = 1; i <= contexts.size(); i++) {
            Boolean verdict = verdicts.get(i);
            if (verdict == null) {
                unjudged++;
            } else if (verdict) {
                relevant++;
                accumulated += (double) relevant / i;
            }
        }
        if (relevant == 0) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", "召回片段全部判定为无关");
            detail.put("chunks", contexts.size());
            if (unjudged > 0) {
                detail.put("unjudgedChunks", unjudged);
            }
            return MetricScore.of(0.0, contexts.size(), detail);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("chunks", contexts.size());
        detail.put("relevantChunks", relevant);
        if (unjudged > 0) {
            detail.put("unjudgedChunks", unjudged);
        }
        return MetricScore.of(accumulated / relevant, contexts.size(), detail);
    }

    private Map<Integer, Boolean> judge(MetricContext context, String query, List<String> contexts) {
        JudgeResult result = context.judge().judge(
                "你是一个严格的评分者。只输出符合给定 JSON 结构的判定结果，不要输出任何多余文本。",
                PROMPT.formatted(MetricSupport.truncate(query), MetricSupport.numbered(contexts)));

        JsonNode array = MetricSupport.arrayNode(MetricSupport.extractJson(result == null ? null : result.output()),
                "verdicts");
        if (array == null) {
            return Map.of();
        }

        Map<Integer, Boolean> indexed = new LinkedHashMap<>();
        List<Boolean> positional = new ArrayList<>();
        for (JsonNode item : array) {
            Boolean relevant = MetricSupport.toVerdict(item, "relevant");
            if (relevant == null) {
                continue;
            }
            JsonNode index = item.isObject() ? item.get("index") : null;
            if (index != null && index.canConvertToInt()) {
                indexed.putIfAbsent(index.asInt(), relevant);
            } else {
                positional.add(relevant);
            }
        }
        // 带序号的优先：部分厂商会把 [{"index":1,...}] 简化成 [true, false]，那种情况按顺序回挂；
        // 两者混用时不带序号的那批位置不可信，当未判定丢掉而不是猜一个名次
        if (!indexed.isEmpty()) {
            return indexed;
        }
        Map<Integer, Boolean> byOrder = new LinkedHashMap<>();
        for (int i = 0; i < positional.size(); i++) {
            byOrder.put(i + 1, positional.get(i));
        }
        return byOrder;
    }
}
