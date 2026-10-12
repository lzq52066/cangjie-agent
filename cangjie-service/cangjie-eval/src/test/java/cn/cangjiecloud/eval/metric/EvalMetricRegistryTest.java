package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EvalMetricRegistry} 单元测试。
 * <p>
 * 注册表是「加指标不改主流程」的唯一入口，它的取舍规则直接决定报告形状：重名会静默覆盖读数，
 * 单个指标炸掉会带走同批次其它指标的分数 —— 两种都是最难从报告里看出来的错。
 */
class EvalMetricRegistryTest {

    /** 只关心接线行为的假指标：名字固定，行为按需注入 */
    private record FakeMetric(String name, boolean supported,
                              java.util.function.Function<EvalSample, MetricScore> action) implements EvalMetric {

        @Override
        public boolean supports(EvalSample sample, MetricContext context) {
            return supported;
        }

        @Override
        public MetricScore score(EvalSample sample, MetricContext context) {
            return action.apply(sample);
        }
    }

    private static FakeMetric scoring(String name, double value) {
        return new FakeMetric(name, true, sample -> MetricScore.of(value));
    }

    private static final EvalSample SAMPLE = new EvalSample("q", "a", List.of(), List.of(), List.of(), null);
    private static final MetricContext CONTEXT = new MetricContext(null, null);

    @Nested
    class Registration {

        /** 按注册顺序保留、重名只留前者：后者被静默丢掉时要能从日志里找到是谁 */
        @Test
        void shouldKeepFirstOfDuplicateNames() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(
                    scoring("faithfulness", 0.9), scoring("faithfulness", 0.1), scoring("contextPrecision", 0.5)));

            assertThat(registry.all()).extracting(EvalMetric::name)
                    .containsExactly("faithfulness", "contextPrecision");
            assertThat(registry.find("faithfulness")).isSameAs(registry.all().get(0));
        }

        /** 空列表、null 元素、没名字的指标都不该让注册表炸掉 */
        @Test
        void shouldIgnoreUnusableRegistrations() {
            EvalMetric blank = new FakeMetric("  ", true, sample -> MetricScore.of(1.0));

            assertThat(new EvalMetricRegistry(null).all()).isEmpty();
            assertThat(new EvalMetricRegistry(List.of()).all()).isEmpty();
            // List.of 不收 null，用 Arrays.asList 造一个带 null 的列表
            assertThat(new EvalMetricRegistry(Arrays.asList(scoring("a", 1.0), null)).all())
                    .extracting(EvalMetric::name).containsExactly("a");
            assertThat(new EvalMetricRegistry(List.of(blank)).all()).isEmpty();
        }

        @Test
        void findShouldTolerateNullAndUnknownNames() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(scoring("faithfulness", 0.9)));

            assertThat(registry.find(null)).isNull();
            assertThat(registry.find("nope")).isNull();
            assertThat(registry.find("faithfulness")).isNotNull();
        }
    }

    @Nested
    class Resolve {

        /** 不点名就是全开：能力不足的指标自己 supports=false 跳过，不需要再加一层开关 */
        @Test
        void emptySelectionShouldEnableAll() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(scoring("a", 1.0), scoring("b", 1.0)));

            assertThat(registry.resolve(null)).hasSize(2);
            assertThat(registry.resolve(List.of())).hasSize(2);
        }

        /** 点名按点名的顺序跑，顺带把空格与重复项收掉 */
        @Test
        void shouldFollowGivenOrder() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(
                    scoring("a", 1.0), scoring("b", 1.0), scoring("c", 1.0)));

            assertThat(registry.resolve(List.of("c", " a ", "a"))).extracting(EvalMetric::name)
                    .containsExactly("c", "a");
        }

        /** 拼错指标名只跳过，不能让整轮评估失败 */
        @Test
        void unknownNamesShouldBeSkippedNotFatal() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(scoring("a", 1.0)));

            assertThat(registry.resolve(List.of("typo", "a"))).extracting(EvalMetric::name).containsExactly("a");
            assertThat(registry.resolve(List.of("typo"))).isEmpty();
        }
    }

    @Nested
    class Execution {

        @Test
        void shouldReturnEmptyWithoutSelection() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(scoring("a", 1.0)));

            assertThat(registry.run(SAMPLE, CONTEXT, null)).isEmpty();
            assertThat(registry.run(SAMPLE, CONTEXT, List.of())).isEmpty();
        }

        /** 输出 map 保留指标顺序：前端按列名动态渲染时不能每次换个次序 */
        @Test
        void shouldPreserveOrderInResult() {
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(scoring("b", 0.4), scoring("a", 0.8)));

            Map<String, MetricScore> scores = registry.run(SAMPLE, CONTEXT, registry.all());

            assertThat(scores.keySet()).containsExactly("b", "a");
            assertThat(scores.get("a").value()).isEqualTo(0.8);
        }

        /** 不适用的指标记 skipped：它不该进均值，也不该把 case 标成 partial */
        @Test
        void unsupportedMetricShouldBeSkipped() {
            FakeMetric skipped = new FakeMetric("contextRecall", false, sample -> MetricScore.of(1.0));

            Map<String, MetricScore> scores = new EvalMetricRegistry(List.of(skipped))
                    .run(SAMPLE, CONTEXT, List.of(skipped));

            assertThat(scores.get("contextRecall").evaluated()).isFalse();
            assertThat(scores.get("contextRecall").detail()).containsKey(MetricScore.SKIP_REASON);
            assertThat(scores.get("contextRecall").detail()).doesNotContainKey(MetricScore.ERROR_REASON);
        }

        /**
         * 一个指标抛异常只让它自己没分：其余指标必须照常出分。
         * <p>
         * 「加了一个新指标结果整轮评估少了一半数」是最难查的回归，异常边界就得钉在这里。
         */
        @Test
        void throwingMetricShouldNotAffectSiblings() {
            EvalMetric boom = new FakeMetric("answerRelevancy", true,
                    sample -> {
                        throw new IllegalStateException("判定服务超时");
                    });
            EvalMetricRegistry registry = new EvalMetricRegistry(List.of(boom, scoring("faithfulness", 0.75)));

            Map<String, MetricScore> scores = registry.run(SAMPLE, CONTEXT, registry.all());

            assertThat(scores.get("answerRelevancy").evaluated()).isFalse();
            assertThat(String.valueOf(scores.get("answerRelevancy").detail().get(MetricScore.ERROR_REASON)))
                    .contains("判定服务超时");
            assertThat(scores.get("faithfulness").value()).isEqualTo(0.75);
        }

        /** 异常没有 message 时用类名兜底，否则原因栏是空的，读报告的人无从判断 */
        @Test
        void exceptionWithoutMessageShouldFallBackToClassName() {
            EvalMetric boom = new FakeMetric("m", true, sample -> {
                throw new NullPointerException();
            });

            Map<String, MetricScore> scores = new EvalMetricRegistry(List.of(boom))
                    .run(SAMPLE, CONTEXT, List.of(boom));

            assertThat(String.valueOf(scores.get("m").detail().get(MetricScore.ERROR_REASON)))
                    .contains("NullPointerException");
        }

        /** 指标自己也不能返回 null：宁可记成「没出分 + 原因」，别让残缺数据把汇总带崩 */
        @Test
        void nullScoreShouldBeTreatedAsFailure() {
            FakeMetric silent = new FakeMetric("m", true, sample -> null);

            Map<String, MetricScore> scores = new EvalMetricRegistry(List.of(silent))
                    .run(SAMPLE, CONTEXT, List.of(silent));

            assertThat(scores.get("m").evaluated()).isFalse();
            assertThat(scores.get("m").detail()).containsKey(MetricScore.ERROR_REASON);
        }

        /**
         * 越界分数在注册表这一道闸门就被拦成「未评测」：第三方指标实现各写各的，
         * 一个 1.5 或 -3 直接进汇总会把均值、回归门禁一起带跑，而报告上看不出异常。
         */
        @Test
        void outOfRangeScoreShouldBecomeFailure() {
            FakeMetric tooHigh = new FakeMetric("m", true, sample -> MetricScore.of(1.5));
            FakeMetric negative = new FakeMetric("n", true, sample -> MetricScore.of(-3.0));

            Map<String, MetricScore> scores = new EvalMetricRegistry(List.of(tooHigh, negative))
                    .run(SAMPLE, CONTEXT, List.of(tooHigh, negative));

            assertThat(scores.get("m").evaluated()).isFalse();
            assertThat(String.valueOf(scores.get("m").detail().get(MetricScore.ERROR_REASON)))
                    .contains("0~1");
            assertThat(scores.get("n").evaluated()).isFalse();
        }

        /** 边界值 0 与 1 是合法读数，不能被当成越界吞掉 */
        @Test
        void boundaryScoresShouldStayEvaluated() {
            FakeMetric zero = new FakeMetric("z", true, sample -> MetricScore.of(0.0));
            FakeMetric one = new FakeMetric("o", true, sample -> MetricScore.of(1.0));

            Map<String, MetricScore> scores = new EvalMetricRegistry(List.of(zero, one))
                    .run(SAMPLE, CONTEXT, List.of(zero, one));

            assertThat(scores.get("z").evaluated()).isTrue();
            assertThat(scores.get("o").value()).isEqualTo(1.0);
        }

        /**
         * 指标名不许占用内置列名：SPI 指标写进 eval_result 时与 recall / correctness 两套读数
         * 会互相覆盖，报告里根本分不出这一列是谁写的。
         */
        @Test
        void reservedNamesShouldBeRejectedAtRegistration() {
            assertThat(new EvalMetricRegistry(List.of(
                    scoring("recall", 0.5), scoring("correctness", 0.5), scoring("faithfulness", 0.5)))
                    .all()).extracting(EvalMetric::name).containsExactly("faithfulness");
        }
    }
}
