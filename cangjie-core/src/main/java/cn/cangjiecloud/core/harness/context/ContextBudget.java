package cn.cangjiecloud.core.harness.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文预算分配器。
 * <p>
 * 总预算内按槽位占比裁剪：不可压缩槽位（系统消息、本轮输入）先全额扣除，
 * 剩余额度再按占比分给可压缩槽位，超出的片段从尾部丢弃并记录丢弃比例。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextBudget {

    /** 是否启用（false 时管线只做排序拼装，行为等价于历史实现） */
    private boolean enabled;

    /** 总 token 预算 */
    private int budgetTokens;

    /** slot.key() -> 占比 */
    private Map<String, Double> slotRatios;

    /** 默认占比（启用预算但未配置时使用） */
    public static final Map<String, Double> DEFAULT_RATIOS = Map.of(
            ContextSlot.SYSTEM_PRE.key(), 0.05,
            ContextSlot.SYSTEM.key(), 0.20,
            ContextSlot.RAG.key(), 0.30,
            ContextSlot.MEMORY.key(), 0.15,
            ContextSlot.HISTORY.key(), 0.30,
            ContextSlot.USER.key(), 0.10);

    public static ContextBudget disabled() {
        return ContextBudget.builder().enabled(false).budgetTokens(0).slotRatios(DEFAULT_RATIOS).build();
    }

    public static ContextBudget of(int budgetTokens, Map<String, Double> ratios) {
        Map<String, Double> merged = new LinkedHashMap<>(DEFAULT_RATIOS);
        if (ratios != null) {
            ratios.forEach((k, v) -> {
                ContextSlot slot = ContextSlot.of(k);
                if (slot != null && v != null && v > 0) {
                    merged.put(slot.key(), v);
                }
            });
        }
        return ContextBudget.builder()
                .enabled(budgetTokens > 0)
                .budgetTokens(budgetTokens)
                .slotRatios(merged)
                .build();
    }

    public double ratioOf(ContextSlot slot) {
        Double ratio = slotRatios == null ? null : slotRatios.get(slot.key());
        return ratio == null ? 0d : ratio;
    }

    /**
     * 对片段列表做预算裁剪（原地修改 {@code droppedRatio} 并返回保留结果）。
     * <p>
     * 裁剪粒度为片段内消息，从列表尾部开始丢弃；未启用预算时原样返回。
     */
    public List<ContextFragment> apply(List<ContextFragment> fragments) {
        if (!enabled || budgetTokens <= 0 || fragments == null || fragments.isEmpty()) {
            return fragments;
        }
        int fixed = 0;
        int compressibleCount = 0;
        for (ContextFragment f : fragments) {
            f.refreshEstTokens();
            if (!f.isCompressible()) {
                fixed += f.getEstTokens();
            } else {
                compressibleCount++;
            }
        }
        int remaining = Math.max(0, budgetTokens - fixed);
        if (compressibleCount == 0) {
            return fragments;
        }
        double totalRatio = fragments.stream()
                .filter(ContextFragment::isCompressible)
                .mapToDouble(f -> ratioOf(f.getSlot()))
                .sum();
        for (ContextFragment f : fragments) {
            if (!f.isCompressible()) {
                continue;
            }
            double share = totalRatio <= 0 ? 1d / compressibleCount : ratioOf(f.getSlot()) / totalRatio;
            int allowance = (int) Math.floor(remaining * share);
            f.setBudgetTokens(allowance);
        }
        return fragments;
    }
}
