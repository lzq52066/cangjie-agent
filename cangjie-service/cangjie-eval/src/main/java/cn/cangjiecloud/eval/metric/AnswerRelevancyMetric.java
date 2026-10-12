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
 * 答案相关性（answer relevancy）：回答是不是在答这个问题。
 * <p>
 * 口径照业界做法反向做：不直接比「问题 vs 回答」的字面像不像，而是让模型从回答反推出
 * 几个「这段回答像是在回应哪个问题」，再取这些反推问题与原问题的向量余弦均值。
 * 答非所问、绕开重点、把三个问题混成一段，反推出来的问题就会跑偏，分数自然掉 ——
 * 比用 embedding 硬算一问一答的相似度灵敏得多（长回答的向量很容易被通用词稀释）。
 * <p>
 * 明确「没在回答问题」（反推不出任何问题）记 0 分：这是真实的相关性失败，不是没评测。
 * 但缺 embedding 能力时一律按未评测跳过 —— 退化成字面匹配得到的分数与真身同名不同义，
 * 会把检索调参带偏。
 */
@Component
public class AnswerRelevancyMetric implements EvalMetric {

    private static final int DEFAULT_QUESTIONS = 3;

    private static final String QUESTION_PROMPT = """
            读下面的「回答」，反推它最可能是在回应哪 %d 个问题：用与提问者同一视角、同一口吻改写，
            只依据回答实际讲到的内容，不要补回答里没有的信息。
            如果回答压根没在回应任何具体问题（客套、拒绝、免责声明），返回空数组。
            只输出 JSON：{"questions": ["问题1", "问题2"]}

            回答：%s
            """;

    @Override
    public String name() {
        return "answerRelevancy";
    }

    @Override
    public String displayName() {
        return "答案相关性";
    }

    @Override
    public boolean supports(EvalSample sample, MetricContext context) {
        return sample != null && sample.query() != null && !sample.query().isBlank()
                && sample.answer() != null && !sample.answer().isBlank()
                && context != null && context.hasJudge() && context.hasEmbedding();
    }

    @Override
    public MetricScore score(EvalSample sample, MetricContext context) throws Exception {
        int questionCount = context.intConfig("answerRelevancy.questions", DEFAULT_QUESTIONS);
        List<String> generated = reverseQuestions(context, sample.answer(), questionCount);
        if (generated == null) {
            return MetricScore.failed("反推问题输出无法解析");
        }
        if (generated.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", "回答未能反推出任何问题（疑似答非所问或直接拒绝）");
            return MetricScore.of(0.0, 0, detail);
        }

        List<String> texts = new ArrayList<>(generated);
        texts.add(sample.query());
        List<float[]> vectors = context.embedding().embed(texts);
        if (vectors == null || vectors.size() != texts.size() || vectors.get(texts.size() - 1) == null) {
            return MetricScore.failed("向量化结果与文本条数不匹配，无法计算余弦");
        }
        float[] queryVector = vectors.get(texts.size() - 1);

        double sum = 0.0;
        List<Double> perQuestion = new ArrayList<>(generated.size());
        for (int i = 0; i < generated.size(); i++) {
            double similarity = MetricSupport.cosine(vectors.get(i), queryVector);
            perQuestion.add(similarity);
            sum += similarity;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("generatedQuestions", generated);
        detail.put("similarities", perQuestion);
        return MetricScore.of(sum / generated.size(), generated.size(), detail);
    }

    /**
     * @return 反推出的问题；输出里压根没有 questions 数组时返回 null
     *         （与模型确实判定「这不是在回答问题」的空数组区分开）
     */
    private List<String> reverseQuestions(MetricContext context, String answer, int questionCount) {
        JudgeResult result = context.judge().judge(
                "你是严格的信息抽取器。只输出符合要求的 JSON，不要输出任何多余文本。",
                QUESTION_PROMPT.formatted(questionCount, MetricSupport.truncate(answer)));
        JsonNode node = MetricSupport.extractJson(result == null ? null : result.output());
        JsonNode array = MetricSupport.arrayNode(node, "questions");
        if (node == null || array == null) {
            return null;
        }
        List<String> questions = MetricSupport.toTextList(array);
        return questions.size() > questionCount ? questions.subList(0, questionCount) : questions;
    }
}
