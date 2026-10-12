package cn.cangjiecloud.eval.metric;

import cn.cangjiecloud.core.eval.EvalMetric;
import cn.cangjiecloud.core.eval.EvalSample;
import cn.cangjiecloud.core.eval.MetricContext;
import cn.cangjiecloud.core.eval.MetricScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标注册表与执行器。
 * <p>
 * Spring 把容器里所有 {@link EvalMetric} 注进来，加一个指标 = 新增一个 {@code @Component}，
 * 主流程与表结构都不用动 —— 这就是把指标做成 SPI 而非写死在评测循环里的全部意义。
 * <p>
 * 执行时逐指标兜异常：一个指标的判定调用挂了，只让这一个指标没分，
 * 既不能拖垮整条 case，更不能让同批次的其它指标跟着一起消失。
 */
@Slf4j
@Component
public class EvalMetricRegistry {

    private final Map<String, EvalMetric> metrics;

    /**
     * 已有真实列的指标名（文档口径召回、判定正确性）。
     * <p>
     * 这两个名字走的是固定列与固定汇总槽位，SPI 指标若同名会把读数额外并进列口径，
     * 报告上出现两行名字一样、来源不一样的分数。注册时直接拒掉，别等汇总去猜。
     */
    private static final Set<String> RESERVED_NAMES = Set.of("recall", "correctness");

    public EvalMetricRegistry(List<EvalMetric> metrics) {
        Map<String, EvalMetric> byName = new LinkedHashMap<>();
        for (EvalMetric metric : metrics == null ? Collections.<EvalMetric>emptyList() : metrics) {
            if (metric == null || metric.name() == null || metric.name().isBlank()) {
                continue;
            }
            if (RESERVED_NAMES.contains(metric.name())) {
                log.warn("评估指标名与内置列冲突，已忽略该实现: name={}, class={}",
                        metric.name(), metric.getClass().getSimpleName());
                continue;
            }
            EvalMetric previous = byName.putIfAbsent(metric.name(), metric);
            if (previous != null) {
                // 同名指标会静默覆盖对方的读数，报表上的均值从此来自哪一份都不确定
                log.warn("评估指标重名，忽略后者: name={}, kept={}, ignored={}",
                        metric.name(), previous.getClass().getSimpleName(), metric.getClass().getSimpleName());
            }
        }
        this.metrics = Collections.unmodifiableMap(byName);
    }

    /** 全部已注册指标（按注册顺序） */
    public List<EvalMetric> all() {
        return List.copyOf(metrics.values());
    }

    public EvalMetric find(String name) {
        return name == null ? null : metrics.get(name);
    }

    /**
     * 本次要跑的指标。
     * <p>
     * 默认全开：能力不足（没配判定模型 / 没配 embedding）的指标会自己 {@code supports=false} 跳过，
     * 因此「不配模型」天然只剩零成本指标，不需要额外一层开关。显式点名时按点名的顺序跑，
     * 点了不存在的名字只记日志 —— 拼错指标名不该让整轮评估失败。
     */
    public List<EvalMetric> resolve(List<String> names) {
        if (names == null || names.isEmpty()) {
            return all();
        }
        List<EvalMetric> selected = new ArrayList<>(names.size());
        for (String name : names) {
            EvalMetric metric = find(name == null ? null : name.trim());
            if (metric == null) {
                log.warn("未知的评估指标，已跳过: {}（可用：{}）", name, metrics.keySet());
            } else if (!selected.contains(metric)) {
                selected.add(metric);
            }
        }
        return selected;
    }

    /**
     * 跑一批指标。返回的 map 保留指标顺序，key 是指标名。
     * <p>
     * 出分一律过 {@link MetricScore#clamp}：指标是对外扩展点，第三方实现返回 1.5、NaN
     * 或负数都有可能，这类值直接进累加器就会污染整轮均值与报告。越界按「本该出分却没出」
     * 处理（{@code failed}），不夹成 1.0 —— 猜出来的上限值比一个空洞更误导。
     */
    public Map<String, MetricScore> run(EvalSample sample, MetricContext context, List<EvalMetric> selected) {
        Map<String, MetricScore> scores = new LinkedHashMap<>();
        if (selected == null || selected.isEmpty()) {
            return scores;
        }
        for (EvalMetric metric : selected) {
            String name = metric.name();
            try {
                if (!metric.supports(sample, context)) {
                    scores.put(name, MetricScore.skipped("当前样本或缺少所需能力，指标不适用"));
                    continue;
                }
                MetricScore score = metric.score(sample, context);
                if (score == null) {
                    // 指标自己也不能返回 null：宁可记成「没出分 + 原因」，别让一行残缺数据把汇总带崩
                    scores.put(name, MetricScore.failed("指标未返回结果"));
                    continue;
                }
                Double valid = MetricScore.clamp(score.value());
                if (valid == null && score.evaluated()) {
                    log.warn("指标返回越界分数，按未评测处理: metric={}, value={}", name, score.value());
                    scores.put(name, MetricScore.failed("指标返回的分数不在 0~1 区间: " + score.value()));
                    continue;
                }
                scores.put(name, score);
            } catch (Exception e) {
                log.warn("指标执行失败: metric={}, {}", name, e.getMessage());
                scores.put(name, MetricScore.failed("指标执行异常: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            }
        }
        return scores;
    }
}
