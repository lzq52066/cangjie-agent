package cn.cangjiecloud.core.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指标可用的外部能力。
 * <p>
 * 两项能力都允许为 null：没配判定模型就不能算 LLM 类指标，没配 embedding 就不能算
 * 语义相关度 —— 缺了就把对应指标标成「未评测」，而不是退化成字面匹配之类的近似物，
 * 后者在报告里和真身长得一模一样，却会把优化方向带偏。
 *
 * @param judge     LLM 判定能力，可为 null
 * @param embedding 向量化能力，可为 null
 * @param config    运行期配置（如 topK、maxChunks），键为点分路径
 */
public record MetricContext(JudgeFunction judge, EmbeddingFunction embedding, Map<String, Object> config) {

    public MetricContext {
        // 配置来自前端 JSON，允许出现 null 值；Map.copyOf 会直接抛 NPE，这里按「没有这项配置」处理
        if (config == null || config.isEmpty()) {
            config = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            config.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            config = Map.copyOf(copy);
        }
    }

    public MetricContext(JudgeFunction judge, EmbeddingFunction embedding) {
        this(judge, embedding, Map.of());
    }

    public boolean hasJudge() {
        return judge != null;
    }

    public boolean hasEmbedding() {
        return embedding != null;
    }

    /**
     * 取整数配置，非数字或越界回落到默认值（配置来自前端 JSON，不能让它把评估带崩）。
     * <p>
     * 数字字符串也算：前端表单和 {@code eval_run.config} 里的值经常是 "5" 这种文本，
     * 只认 Number 会让这项配置静默失效、指标按默认值跑，而报告上看不出它没被应用过。
     */
    public int intConfig(String key, int defaultValue) {
        Object raw = config.get(key);
        Integer value = null;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else if (raw != null) {
            try {
                value = Integer.valueOf(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                value = null;
            }
        }
        return value != null && value > 0 ? value : defaultValue;
    }
}
