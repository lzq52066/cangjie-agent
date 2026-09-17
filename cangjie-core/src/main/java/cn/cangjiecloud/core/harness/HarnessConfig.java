package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 应用级 Harness 配置。
 * <p>
 * 从 {@code application.config} 的 {@code harness} 节点反序列化而来，未配置的字段为 null，
 * 由 {@code HarnessConfigResolver} 回落到全局 {@code cangjie.harness.*} 默认值。
 * 放在 config 列而非新增表字段，天然被应用版本快照覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessConfig {

    /** 循环策略覆盖 */
    private Loop loop;

    /** 上下文预算覆盖 */
    private Context context;

    /** 审批策略覆盖 */
    private Approval approval;

    /** 工具白/黑名单与风险覆盖 */
    private ToolPolicy toolPolicy;

    /** 单次 run 的 token 预算（超限优雅收尾，0/null 表示不限制） */
    private Long runTokenBudget;

    /** 子 Agent 最大嵌套深度（null 用全局默认） */
    private Integer subAgentMaxDepth;

    /** 是否记录 step 明细 */
    private Boolean recordSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Loop {
        private Integer maxRounds;
        private Long timeoutSeconds;
        private Boolean parallelTools;
        private Long toolTimeoutSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Context {
        /** 是否启用 token 预算分配（false 时等价于历史行为） */
        private Boolean budgetEnabled;
        private Integer budgetTokens;
        /** slot 名 -> 占比 */
        private Map<String, Double> slotRatios;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Approval {
        private Boolean enabled;
        private Integer timeoutSeconds;
        /** 超时/无审批人时的默认决策：approve / reject */
        private String defaultDecision;
        /** 需要审批的函数名（支持 * 后缀通配，如 delete_*） */
        private List<String> requireFunctions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolPolicy {
        /** 允许列表（为空表示不限制） */
        private List<String> allow;
        /** 拒绝列表，优先级高于 allow */
        private List<String> deny;
        /** 函数名 -> 风险等级 low/medium/high */
        private Map<String, String> riskOverrides;
    }
}
