package cn.cangjiecloud.chat.harness;

import cn.cangjiecloud.core.harness.context.ContextBudget;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent Harness 全局默认配置（{@code cangjie.harness.*}）。
 * <p>
 * 应用级覆盖来自 {@code application.config} 的 {@code harness} 节点，由
 * {@link HarnessConfigResolver} 与本类合并后产出最终执行参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cangjie.harness")
public class HarnessProperties {

    /** Function Calling 最大轮次 */
    private int maxRounds = 5;

    /** 单次 run 总超时（秒） */
    private long timeoutSeconds = 300;

    /** 单次工具调用兜底超时（秒） */
    private long toolTimeoutSeconds = 30;

    /** 工具输出回填模型的最大字符数 */
    private int maxToolOutputChars = 8000;

    /** 是否启用人工审批拦截 */
    private boolean approvalEnabled = false;

    /** 审批单默认有效期（秒） */
    private int approvalTimeoutSeconds = 1800;

    /** 是否向前端推送 tool_start / tool_finish 事件（OpenAI 兼容客户端不应收到非标准帧，默认关闭） */
    private boolean sseToolEvents = false;

    /** 上下文预算默认值 */
    private Context context = new Context();

    @Data
    public static class Context {
        private boolean budgetEnabled = false;
        private int budgetTokens = 0;
        private Map<String, Double> slotRatios = ContextBudget.DEFAULT_RATIOS;

        public ContextBudget toBudget() {
            return budgetEnabled && budgetTokens > 0
                    ? ContextBudget.of(budgetTokens, slotRatios)
                    : ContextBudget.disabled();
        }
    }
}
