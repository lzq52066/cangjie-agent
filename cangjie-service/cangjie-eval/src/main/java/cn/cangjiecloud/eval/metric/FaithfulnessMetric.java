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
 * 忠实度（faithfulness）：回答里的断言有多少被本次检索片段支撑。
 * <p>
 * 这是 RAG 最该看的指标 —— 它和「答得对不对」无关，专门抓「检索没召回却编得很顺」的幻觉。
 * 口径照业界两路走：先把回答拆成独立断言，再逐条判断能否由片段推出，分数 = 被支撑数 / 全部断言数。
 * <p>
 * 拿不到判定的断言按「未被支撑」计（保守）：分母固定是全部断言，否则模型少答几条判定
 * 就能把分数抬高；缺失条数一并写进 {@code detail.unjudgedClaims}，读数的人知道下限在哪。
 */
@Component
public class FaithfulnessMetric implements EvalMetric {

    private static final String CLAIM_PROMPT = """
            把下面的「回答」拆成若干条互相独立的断言（每条只讲一件事），保留原文的说法与限定词，不要合并、不要评价对错。
            只输出 JSON：{"claims": ["断言1", "断言2"]}
            回答没有可核查的事实性断言（纯客套或拒绝回答）时输出 {"claims": []}。

            问题：%s

            回答：%s
            """;

    private static final String VERDICT_PROMPT = """
            判断每条「断言」能否由给定的「参考资料」推出：资料里有明确依据记 supported=true，
            资料里没有、或只能靠常识补上记 supported=false。不要因为断言本身是对的就放行。
            只输出 JSON：{"verdicts": [{"index": 断言序号(从 1 开始), "supported": true 或 false, "reason": "一句话依据"}]}
            每条断言都要给出一条 verdict，序号不要漏、不要重复。

            参考资料：
            %s
            断言列表：
            %s
            """;

    @Override
    public String name() {
        return "faithfulness";
    }

    @Override
    public String displayName() {
        return "忠实度";
    }

    @Override
    public boolean supports(EvalSample sample, MetricContext context) {
        return sample != null && sample.answer() != null && !sample.answer().isBlank()
                && context != null && context.hasJudge();
    }

    @Override
    public MetricScore score(EvalSample sample, MetricContext context) {
        if (!sample.hasContexts()) {
            // 没检索到任何东西，回答无从谈起「忠实」——这时 0 分和「没评」在报告里含义完全不同
            return MetricScore.skipped("本次没有检索到参考片段，忠实度不适用");
        }

        List<String> claims = extractClaims(context, sample);
        if (claims == null) {
            // 拆不出断言列表与「回答里没有断言」是两件事：前者是判定挂了，不能当成后者好心的跳过
            return MetricScore.failed("断言拆解输出无法解析");
        }
        if (claims.isEmpty()) {
            return MetricScore.skipped("回答未拆解出可核查的断言");
        }
        Map<Integer, Boolean> verdicts = judgeClaims(context, sample, claims);

        int supported = 0;
        int unjudged = 0;
        for (int i = 1; i <= claims.size(); i++) {
            Boolean verdict = verdicts.get(i);
            if (verdict == null) {
                unjudged++;
            } else if (verdict) {
                supported++;
            }
        }
        if (unjudged == claims.size()) {
            // 整批判定都没落回来：这是输出格式问题，不是「回答全是幻觉」
            return MetricScore.failed("判定输出无法与断言对齐");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("claims", claims);
        detail.put("supportedClaims", supported);
        detail.put("totalClaims", claims.size());
        if (unjudged > 0) {
            detail.put("unjudgedClaims", unjudged);
        }
        double score = (double) supported / claims.size();
        return MetricScore.of(score, claims.size(), detail);
    }

    /**
     * @return 拆解出的断言；输出根本不是一个 JSON 数组时返回 null（与「回答确实没断言」的空数组区分开）
     */
    private List<String> extractClaims(MetricContext context, EvalSample sample) {
        JudgeResult result = context.judge().judge(
                "你是严格的信息抽取器。只输出符合要求的 JSON，不要输出任何多余文本。",
                CLAIM_PROMPT.formatted(MetricSupport.truncate(sample.query()),
                        MetricSupport.truncate(sample.answer())));
        JsonNode node = MetricSupport.extractJson(result == null ? null : result.output());
        JsonNode array = MetricSupport.arrayNode(node, "claims");
        if (node == null || array == null) {
            return null;
        }
        return MetricSupport.toTextList(array);
    }

    /**
     * 一次调用判完全部断言，按 index 回挂 —— 逐条调用会把成本推到断言条数量级，
     * 而按下标对齐比按顺序对齐抗得住模型漏答。
     */
    private Map<Integer, Boolean> judgeClaims(MetricContext context, EvalSample sample, List<String> claims) {
        String contextBlock = MetricSupport.numbered(sample.contextTexts());
        StringBuilder claimBlock = new StringBuilder();
        for (int i = 0; i < claims.size(); i++) {
            claimBlock.append(i + 1).append(". ").append(claims.get(i)).append('\n');
        }
        JudgeResult result = context.judge().judge(
                "你是一个严格的评分者。只输出符合给定 JSON 结构的判定结果，不要输出任何多余文本。",
                VERDICT_PROMPT.formatted(contextBlock, claimBlock));

        Map<Integer, Boolean> verdicts = new LinkedHashMap<>();
        JsonNode array = MetricSupport.arrayNode(MetricSupport.extractJson(result == null ? null : result.output()),
                "verdicts");
        if (array == null) {
            return verdicts;
        }
        List<Boolean> fallback = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isObject()) {
                Boolean supported = MetricSupport.toVerdict(item, "supported");
                JsonNode index = item.get("index");
                if (supported != null && index != null && index.canConvertToInt()) {
                    verdicts.putIfAbsent(index.asInt(), supported);
                } else if (supported != null) {
                    fallback.add(supported);
                }
            } else {
                Boolean supported = MetricSupport.toVerdict(item, "supported");
                if (supported != null) {
                    fallback.add(supported);
                }
            }
        }
        // 模型不给序号时按顺序回挂：部分厂商会把 [{"index":1,...}] 简化成 [true, false]
        if (verdicts.isEmpty()) {
            for (int i = 0; i < fallback.size() && i < claims.size(); i++) {
                verdicts.put(i + 1, fallback.get(i));
            }
        }
        return verdicts;
    }
}
