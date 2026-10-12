package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.core.eval.ContextChunk;
import cn.cangjiecloud.core.eval.EmbeddingFunction;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.JudgeFunction;
import cn.cangjiecloud.core.eval.JudgeResult;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四个 RAGAS 口径指标的单元测试。
 * <p>
 * 全部用脚本化假判定器，不碰真模型：指标的正确性只能靠「给定这份模型输出，分数应当是多少」
 * 来钉住。重点盯三类分支 —— 出分、结构性不适用（skipped）、本该出分却没出（failed），
 * 这三者在报告里含义完全不同，混在一起就会出现「把没评测显示成零分」那种误导读数。
 */
class EvalMetricsTest {

    private static final List<ContextChunk> TWO_CHUNKS = List.of(
            new ContextChunk("仓颉是华为推出的编程语言", "p1", "doc1"),
            new ContextChunk("今天天气不错", "p2", "doc2"));

    /** 按调用顺序回放输出的假判定器，同时记下每次的 system / prompt */
    private static final class ScriptedJudge implements JudgeFunction {

        private final List<String> outputs;
        private final List<String> systems = new ArrayList<>();
        private final List<String> prompts = new ArrayList<>();

        ScriptedJudge(String... outputs) {
            this.outputs = List.of(outputs);
        }

        @Override
        public JudgeResult judge(String system, String prompt) {
            systems.add(system);
            prompts.add(prompt);
            int index = prompts.size() - 1;
            // 脚本用完还继续被调用：说明指标多跑了轮次，回 null 让它暴露成「未评测」而不是假分数
            return JudgeResult.of(index < outputs.size() ? outputs.get(index) : null);
        }

        int calls() {
            return prompts.size();
        }
    }

    /** 查表式假向量：表里没有的文本给零向量，并记下每次批量入参 */
    private static final class MapEmbedding implements EmbeddingFunction {

        private final Map<String, float[]> vectors;
        private final List<List<String>> batches = new ArrayList<>();
        private int shortfall;

        MapEmbedding(Map<String, float[]> vectors) {
            this.vectors = vectors;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            batches.add(List.copyOf(texts));
            List<float[]> result = new ArrayList<>();
            int limit = texts.size() - shortfall;
            for (int i = 0; i < limit; i++) {
                float[] vector = vectors.get(texts.get(i));
                result.add(vector == null ? new float[]{0f, 0f} : vector);
            }
            return result;
        }
    }

    private static MetricContext judgeOnly(ScriptedJudge judge) {
        return new MetricContext(judge, null);
    }

    private static EvalSample sample(String query, String answer, List<ContextChunk> contexts) {
        return new EvalSample(query, answer, contexts, List.of(), List.of(), null);
    }

    @Nested
    class Faithfulness {

        private final FaithfulnessMetric metric = new FaithfulnessMetric();

        /** 名字与展示名：extra_scores 的键就是 name()，前端按它动态出列 */
        @Test
        void shouldDeclareStableName() {
            assertThat(metric.name()).isEqualTo("faithfulness");
            assertThat(metric.displayName()).isEqualTo("忠实度");
            assertThat(metric.requiresGroundTruth()).isFalse();
        }

        /** 缺回答或缺判定能力都不该跑：没有向量就没断言，没有判定就只能瞎猜 */
        @Test
        void shouldRequireAnswerAndJudge() {
            assertThat(metric.supports(null, judgeOnly(new ScriptedJudge()))).isFalse();
            assertThat(metric.supports(sample("q", null, TWO_CHUNKS), judgeOnly(new ScriptedJudge()))).isFalse();
            assertThat(metric.supports(sample("q", "  ", TWO_CHUNKS), judgeOnly(new ScriptedJudge()))).isFalse();
            assertThat(metric.supports(sample("q", "仓颉是语言", TWO_CHUNKS), new MetricContext(null, null)))
                    .isFalse();
            assertThat(metric.supports(sample("q", "仓颉是语言", TWO_CHUNKS), judgeOnly(new ScriptedJudge())))
                    .isTrue();
        }

        /** 两条断言判回一条支撑 → 0.5，中间量落在 detail 里供追溯 */
        @Test
        void shouldScoreSupportedShare() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"claims\": [\"仓颉是华为推出的编程语言\", \"仓颉支持跨端\"]}",
                    "{\"verdicts\": [{\"index\": 1, \"supported\": true}, {\"index\": 2, \"supported\": false}]}");

            MetricScore score = metric.score(sample("仓颉是什么？", "仓颉是华为的编程语言，支持跨端", TWO_CHUNKS),
                    judgeOnly(judge));

            assertThat(score.evaluated()).isTrue();
            assertThat(score.value()).isEqualTo(0.5);
            assertThat(score.sampleCount()).isEqualTo(2);
            assertThat(score.detail())
                    .containsEntry("supportedClaims", 1)
                    .containsEntry("totalClaims", 2)
                    .doesNotContainKey("unjudgedClaims");
            assertThat((List<String>) score.detail().get("claims"))
                    .containsExactly("仓颉是华为推出的编程语言", "仓颉支持跨端");
        }

        /** 漏判的断言按不支撑计（保守）：分母固定是全部断言，否则少答几条就能抬高分数 */
        @Test
        void shouldCountUnjudgedClaimsAgainstScore() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"claims\": [\"甲\", \"乙\", \"丙\"]}",
                    "{\"verdicts\": [{\"index\": 1, \"supported\": true}]}");

            MetricScore score = metric.score(sample("q", "甲乙丙", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.value()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(score.detail()).containsEntry("unjudgedClaims", 2);
        }

        /** 模型不给序号时按顺序回挂：部分厂商会把对象数组简化成 [true, false] */
        @Test
        void shouldFallBackToPositionalOrder() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"claims\": [\"甲\", \"乙\"]}",
                    "{\"verdicts\": [true, false]}");

            assertThat(metric.score(sample("q", "甲乙", TWO_CHUNKS), judgeOnly(judge)).value()).isEqualTo(0.5);
        }

        /** 整批判定都对不上是输出格式问题，不能当成「回答全是幻觉」给个 0 分 */
        @Test
        void shouldFailWhenVerdictsCannotBeAligned() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"claims\": [\"甲\", \"乙\"]}",
                    "{\"somethingElse\": 1}");

            MetricScore score = metric.score(sample("q", "甲乙", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.evaluated()).isFalse();
            assertThat(score.detail()).containsKey(MetricScore.ERROR_REASON);
        }

        /** 拆不出断言列表（压根不是 JSON）与「回答确实没断言」是两件事：前者是判定挂了 */
        @Test
        void shouldFailWhenClaimExtractionUnparseable() {
            ScriptedJudge judge = new ScriptedJudge("抱歉，我没法拆解。");

            MetricScore score = metric.score(sample("q", "甲乙", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.evaluated()).isFalse();
            assertThat(String.valueOf(score.detail().get(MetricScore.ERROR_REASON))).contains("断言拆解");
        }

        /** 纯客套回答没得可核：跳过而不是判它忠实 */
        @Test
        void shouldSkipWhenNoClaims() {
            ScriptedJudge judge = new ScriptedJudge("{\"claims\": []}");

            MetricScore score = metric.score(sample("q", "很高兴帮到你", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.evaluated()).isFalse();
            assertThat(score.detail()).containsKey(MetricScore.SKIP_REASON);
        }

        /** 没召回到片段时不调模型，也不出分 */
        @Test
        void shouldSkipWithoutContexts() {
            ScriptedJudge judge = new ScriptedJudge("{\"claims\": [\"甲\"]}");

            MetricScore score = metric.score(sample("q", "甲", List.of()), judgeOnly(judge));

            assertThat(score.evaluated()).isFalse();
            assertThat(score.detail()).containsKey(MetricScore.SKIP_REASON);
            assertThat(judge.calls()).isZero();
        }

        /** 片段正文与断言都要进 prompt，否则判定无从对齐 */
        @Test
        void shouldFeedContextsAndClaimsIntoVerdictPrompt() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"claims\": [\"甲\"]}",
                    "{\"verdicts\": [{\"index\": 1, \"supported\": true}]}");
            metric.score(sample("仓颉是什么？", "甲", TWO_CHUNKS), judgeOnly(judge));

            assertThat(judge.calls()).isEqualTo(2);
            assertThat(judge.prompts.get(0)).contains("仓颉是什么？").contains("甲");
            assertThat(judge.prompts.get(1)).contains("仓颉是华为推出的编程语言").contains("1. 甲");
            assertThat(judge.systems.get(1)).isNotBlank();
        }
    }

    @Nested
    class ContextPrecision {

        private final ContextPrecisionMetric metric = new ContextPrecisionMetric();

        @Test
        void shouldDeclareNameAndRequirements() {
            assertThat(metric.name()).isEqualTo("contextPrecision");
            assertThat(metric.displayName()).isEqualTo("上下文精度");
        }

        /** 要问题、要片段、要判定器，缺一不可 */
        @Test
        void shouldRequireQueryContextsAndJudge() {
            assertThat(metric.supports(sample(null, "答", TWO_CHUNKS), judgeOnly(new ScriptedJudge()))).isFalse();
            assertThat(metric.supports(sample("q", "答", List.of()), judgeOnly(new ScriptedJudge()))).isFalse();
            assertThat(metric.supports(sample("q", "答", TWO_CHUNKS), new MetricContext(null, null))).isFalse();
            assertThat(metric.supports(sample("q", "答", TWO_CHUNKS), judgeOnly(new ScriptedJudge()))).isTrue();
        }

        /** 有用的片段排在第 2 位：AP@k 名次加权会掉下来，而这正是 recall 看不见的信号 */
        @Test
        void shouldWeightByRank() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"verdicts\": [{\"index\": 1, \"relevant\": false}, {\"index\": 2, \"relevant\": true}]}");

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.value()).isEqualTo(0.5);
            assertThat(score.detail()).containsEntry("relevantChunks", 1).containsEntry("chunks", 2);
        }

        /** 相关的排最前：AP@k = 1.0，同样的召回内容换个名次分数就变了 */
        @Test
        void shouldGiveFullScoreWhenRelevantComesFirst() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"verdicts\": [{\"index\": 1, \"relevant\": true}, {\"index\": 2, \"relevant\": false}]}");

            assertThat(metric.score(sample("q", "答", TWO_CHUNKS), judgeOnly(judge)).value()).isEqualTo(1.0);
        }

        /** 三条片段两条有用但都排在后面：名次加权要按前缀精度累加 */
        @Test
        void shouldAccumulatePrefixPrecision() {
            List<ContextChunk> chunks = List.of(
                    new ContextChunk("噪声", "p1", "doc1"),
                    new ContextChunk("有用一", "p2", "doc1"),
                    new ContextChunk("有用二", "p3", "doc1"));
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"verdicts\": [{\"index\": 1, \"relevant\": false},"
                            + " {\"index\": 2, \"relevant\": true}, {\"index\": 3, \"relevant\": true}]}");

            MetricScore score = metric.score(sample("q", "答", chunks), judgeOnly(judge));

            // (1/2 + 2/3) / 2
            assertThat(score.value()).isCloseTo(0.5833333, org.assertj.core.data.Offset.offset(1e-6));
        }

        /** 全部判为无关是真实的 0 分（噪声塞满上下文），不是没评测 */
        @Test
        void shouldScoreZeroWhenNothingRelevant() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"verdicts\": [{\"index\": 1, \"relevant\": false}, {\"index\": 2, \"relevant\": false}]}");

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.value()).isEqualTo(0.0);
            assertThat(score.detail()).containsEntry("chunks", 2);
        }

        /** 没落回来的片段按不相关计，但要点名条数：0 分到底是「全是噪声」还是「模型没答」得能区分 */
        @Test
        void shouldReportUnjudgedChunks() {
            ScriptedJudge judge = new ScriptedJudge(
                    "{\"verdicts\": [{\"index\": 1, \"relevant\": true}]}");

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.value()).isEqualTo(1.0);
            assertThat(score.detail()).containsEntry("unjudgedChunks", 1);
        }

        /** 判定输出对不上片段时是 failed：这条 case 该被标成 partial，而不是拿 0 分进均值 */
        @Test
        void shouldFailWhenVerdictsUnparseable() {
            ScriptedJudge judge = new ScriptedJudge("判定完成，结果如上文所述。");

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS), judgeOnly(judge));

            assertThat(score.evaluated()).isFalse();
            assertThat(score.detail()).containsKey(MetricScore.ERROR_REASON);
        }
    }

    @Nested
    class ContextRecall {

        private final ContextRecallMetric metric = new ContextRecallMetric();

        @Test
        void shouldDeclareNameAndGroundTruthNeed() {
            assertThat(metric.name()).isEqualTo("contextRecall");
            assertThat(metric.requiresGroundTruth()).isTrue();
        }

        /** 只吃片段级标注：没标注就跳过，拿文档标注冒充片段口径等于把同一件事记两遍 */
        @Test
        void shouldRequireChunkLevelGroundTruth() {
            EvalSample withoutAnnotation = new EvalSample("q", "答", TWO_CHUNKS,
                    List.of("doc1"), List.of(), null);
            EvalSample withAnnotation = new EvalSample("q", "答", TWO_CHUNKS,
                    List.of("doc1"), List.of("p1"), null);

            assertThat(metric.supports(withoutAnnotation, new MetricContext(null, null))).isFalse();
            assertThat(metric.supports(withAnnotation, new MetricContext(null, null))).isTrue();
        }

        /** 标注两个片段、召回中一个 → 0.5，命中与漏检都点名 */
        @Test
        void shouldScoreCoveredShare() {
            EvalSample sample = new EvalSample("q", "答", TWO_CHUNKS,
                    List.of(), List.of("p1", "p3"), null);

            MetricScore score = metric.score(sample, new MetricContext(null, null));

            assertThat(score.value()).isEqualTo(0.5);
            assertThat(score.detail()).containsEntry("hitChunks", List.of("p1"))
                    .containsEntry("missedChunks", List.of("p3"));
        }

        /** 零 LLM 调用：这是个纯集合运算指标，省钱到可以每轮都跑 */
        @Test
        void shouldNotCallJudge() {
            ScriptedJudge judge = new ScriptedJudge("不该被用到");
            EvalSample sample = new EvalSample("q", "答", TWO_CHUNKS, List.of(), List.of("p1"), null);

            metric.score(sample, new MetricContext(judge, null));

            assertThat(judge.calls()).isZero();
        }

        /** 没跑检索（或零召回）时算出的 0 分是伪造读数，跳过 */
        @Test
        void shouldSkipWhenNothingRetrieved() {
            EvalSample sample = new EvalSample("q", "答", List.of(), List.of(), List.of("p1"), null);

            assertThat(metric.score(sample, new MetricContext(null, null)).detail())
                    .containsKey(MetricScore.SKIP_REASON);
        }

        /** 召回片段不带 paragraphId（旧数据或在线回放）时无法按片段对齐，跳过而不是判负 */
        @Test
        void shouldSkipWhenChunksCarryNoParagraphId() {
            List<ContextChunk> anonymous = List.of(new ContextChunk("正文", null, "doc1"));
            EvalSample sample = new EvalSample("q", "答", anonymous, List.of(), List.of("p1"), null);

            assertThat(metric.score(sample, new MetricContext(null, null)).detail())
                    .containsKey(MetricScore.SKIP_REASON);
        }
    }

    @Nested
    class AnswerRelevancy {

        private final AnswerRelevancyMetric metric = new AnswerRelevancyMetric();

        @Test
        void shouldDeclareNameAndDisplayName() {
            assertThat(metric.name()).isEqualTo("answerRelevancy");
            assertThat(metric.displayName()).isEqualTo("答案相关性");
        }

        /** 既要判定器又要向量能力：缺 embedding 时退化字面匹配会造出一个同名不同义的分数 */
        @Test
        void shouldRequireJudgeAndEmbedding() {
            MapEmbedding embedding = new MapEmbedding(Map.of());
            assertThat(metric.supports(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(new ScriptedJudge(), null))).isFalse();
            assertThat(metric.supports(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(null, embedding))).isFalse();
            assertThat(metric.supports(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(new ScriptedJudge(), embedding))).isTrue();
            assertThat(metric.supports(sample(null, "答", TWO_CHUNKS),
                    new MetricContext(new ScriptedJudge(), embedding))).isFalse();
        }

        /** 反推三问与原问题的余弦均值：一问同向、一问正交 → 0.5 */
        @Test
        void shouldAverageCosineOfReversedQuestions() throws Exception {
            ScriptedJudge judge = new ScriptedJudge("{\"questions\": [\"甲是什么？\", \"无关问题\"]}");
            MapEmbedding embedding = new MapEmbedding(Map.of(
                    "甲是什么？", new float[]{1f, 0f},
                    "无关问题", new float[]{0f, 1f},
                    "甲是什么？和乙呢？", new float[]{1f, 0f}));

            MetricScore score = metric.score(sample("甲是什么？和乙呢？", "甲是……", TWO_CHUNKS),
                    new MetricContext(judge, embedding));

            assertThat(score.value()).isEqualTo(0.5);
            assertThat(score.sampleCount()).isEqualTo(2);
            assertThat(score.detail()).containsKey("generatedQuestions");
            // 反推问题与原问题一起批量向量化，只多一次调用
            assertThat(embedding.batches).hasSize(1);
            assertThat(embedding.batches.get(0)).containsExactly("甲是什么？", "无关问题", "甲是什么？和乙呢？");
        }

        /** 答非所问（反推不出任何问题）是真实的相关性失败，记 0 分而不是「未评测」 */
        @Test
        void shouldScoreZeroWhenNothingCanBeReversed() throws Exception {
            ScriptedJudge judge = new ScriptedJudge("{\"questions\": []}");

            MetricScore score = metric.score(sample("q", "很高兴帮到你", TWO_CHUNKS),
                    new MetricContext(judge, new MapEmbedding(Map.of())));

            assertThat(score.value()).isEqualTo(0.0);
            assertThat(score.sampleCount()).isZero();
            assertThat(String.valueOf(score.detail().get("reason"))).contains("答非所问");
        }

        /** 输出压根不是 JSON：这是判定挂了，不能顺手记成「答非所问 0 分」 */
        @Test
        void shouldFailWhenReversedQuestionsUnparseable() throws Exception {
            ScriptedJudge judge = new ScriptedJudge("这个问题很好。");

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(judge, new MapEmbedding(Map.of())));

            assertThat(score.evaluated()).isFalse();
            assertThat(String.valueOf(score.detail().get(MetricScore.ERROR_REASON))).contains("反推问题");
        }

        /** 向量条数与文本条数不匹配时算不出余弦：failed，而不是拿半截结果凑一个分 */
        @Test
        void shouldFailWhenVectorCountMismatch() throws Exception {
            ScriptedJudge judge = new ScriptedJudge("{\"questions\": [\"甲是什么？\", \"乙是什么？\"]}");
            MapEmbedding embedding = new MapEmbedding(Map.of());
            embedding.shortfall = 1;

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(judge, embedding));

            assertThat(score.evaluated()).isFalse();
            assertThat(score.detail()).containsKey(MetricScore.ERROR_REASON);
        }

        /** 反推条数可由 config 收敛（answerRelevancy.questions），省 token 也能调灵敏度 */
        @Test
        void shouldHonourConfiguredQuestionCount() throws Exception {
            ScriptedJudge judge = new ScriptedJudge("{\"questions\": [\"甲是什么？\", \"乙是什么？\", \"丙是什么？\"]}");
            MapEmbedding embedding = new MapEmbedding(Map.of("甲是什么？", new float[]{1f, 0f}, "q", new float[]{1f, 0f}));

            MetricScore score = metric.score(sample("q", "答", TWO_CHUNKS),
                    new MetricContext(judge, embedding, Map.of("answerRelevancy.questions", 1)));

            assertThat(score.value()).isEqualTo(1.0);
            assertThat(score.sampleCount()).isEqualTo(1);
            assertThat(embedding.batches.get(0)).containsExactly("甲是什么？", "q");
        }
    }
}
