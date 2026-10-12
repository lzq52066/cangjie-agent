package cn.cangjiecloud.core.eval;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MetricContext} 单元测试。
 * <p>
 * 重点是 {@code intConfig} 的容错：配置来自前端 JSON 与 {@code eval_run.config}，
 * 「取不到就默默用默认值」是最难发现的一类失效 —— 指标照跑，报告照出，只是配置没生效。
 */
class MetricContextTest {

    private static MetricContext with(Map<String, Object> config) {
        return new MetricContext(null, null, config);
    }

    @Test
    void intConfigShouldAcceptNumbersAndNumericStringsAlike() {
        Map<String, Object> config = new HashMap<>();
        config.put("topK", 5);
        config.put("maxChunks", "8");

        MetricContext context = with(config);

        assertThat(context.intConfig("topK", 3)).isEqualTo(5);
        assertThat(context.intConfig("maxChunks", 3)).isEqualTo(8);
    }

    @Test
    void intConfigShouldFallBackForMissingGarbageAndNonPositive() {
        Map<String, Object> config = new HashMap<>();
        config.put("blank", "  ");
        config.put("text", "abc");
        config.put("zero", 0);
        config.put("negative", -2);

        MetricContext context = with(config);

        assertThat(context.intConfig("absent", 7)).isEqualTo(7);
        assertThat(context.intConfig("blank", 7)).isEqualTo(7);
        assertThat(context.intConfig("text", 7)).isEqualTo(7);
        assertThat(context.intConfig("zero", 7)).isEqualTo(7);
        assertThat(context.intConfig("negative", 7)).isEqualTo(7);
    }

    /** null 键与 null 值按「没有这项配置」处理：Map.copyOf 遇到 null 值会直接抛 NPE */
    @Test
    void nullEntriesShouldBeDroppedInsteadOfBreakingConstruction() {
        Map<String, Object> config = new HashMap<>();
        config.put(null, 1);
        config.put("kept", 2);
        config.put("dropped", null);

        MetricContext context = with(config);

        assertThat(context.intConfig("kept", 9)).isEqualTo(2);
        assertThat(context.intConfig("dropped", 9)).isEqualTo(9);
    }

    @Test
    void capabilitiesShouldReportAbsentAsUnavailable() {
        MetricContext none = new MetricContext(null, null);

        assertThat(none.hasJudge()).isFalse();
        assertThat(none.hasEmbedding()).isFalse();
    }
}
