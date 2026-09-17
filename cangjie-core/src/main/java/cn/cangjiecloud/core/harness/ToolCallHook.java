package cn.cangjiecloud.core.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 工具调用拦截钩子（按 {@link #getOrder()} 升序执行，数值越小越先执行）。
 * <p>
 * 用于参数校验、权限策略、审批拦截、审计埋点等横切关注点，
 * 使 Agent 循环本身不出现任何工具特判分支。
 */
public interface ToolCallHook {

    /**
     * 工具执行前判定
     */
    Decision before(ToolInvocation invocation, HarnessContext context);

    /**
     * 工具执行后回调（审计、指标）
     */
    default void after(ToolInvocation invocation, ToolOutcome outcome, HarnessContext context) {
    }

    /**
     * 执行顺序，默认 500（内置校验 100 / 策略 200 / 审批 300 / 审计 900）
     */
    default int getOrder() {
        return 500;
    }

    /**
     * 钩子判定结果
     */
    @Data
    @Builder
    @AllArgsConstructor
    class Decision {

        public enum Action {
            /** 放行 */
            PROCEED,
            /** 拒绝执行，把原因作为工具结果回喂模型（模型可自行改道） */
            DENY,
            /** 需要人工审批，run 挂起 */
            REQUIRE_APPROVAL
        }

        private Action action;

        /** 拒绝原因 / 审批理由 */
        private String reason;

        /** 风险等级：low / medium / high */
        private String riskLevel;

        public boolean isProceed() {
            return action == Action.PROCEED;
        }

        public boolean isDeny() {
            return action == Action.DENY;
        }

        public boolean isRequireApproval() {
            return action == Action.REQUIRE_APPROVAL;
        }

        public static Decision proceed() {
            return Decision.builder().action(Action.PROCEED).build();
        }

        public static Decision deny(String reason) {
            return Decision.builder().action(Action.DENY).reason(reason).riskLevel("low").build();
        }

        public static Decision requireApproval(String reason, String riskLevel) {
            return Decision.builder().action(Action.REQUIRE_APPROVAL)
                    .reason(reason).riskLevel(riskLevel == null ? "medium" : riskLevel).build();
        }
    }
}
