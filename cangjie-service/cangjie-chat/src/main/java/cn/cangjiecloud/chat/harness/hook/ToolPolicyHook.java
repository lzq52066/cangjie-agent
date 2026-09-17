package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 工具策略钩子（order 200）：应用级白/黑名单与风险等级覆盖。
 * <p>
 * deny 优先于 allow；allow 为空表示不限制。风险覆盖写回 invocation，供后续审批钩子使用。
 */
@Component
public class ToolPolicyHook implements ToolCallHook {

    @Override
    public Decision before(ToolInvocation invocation, HarnessContext context) {
        HarnessConfig config = context.getRequest() == null ? null : context.getRequest().getConfig();
        HarnessConfig.ToolPolicy policy = config == null ? null : config.getToolPolicy();
        if (policy == null) {
            return Decision.proceed();
        }
        String callName = invocation.getCallName();
        if (ToolPatterns.matchesAny(policy.getDeny(), callName)) {
            return Decision.deny("工具 " + callName + " 已被应用策略禁用");
        }
        if (policy.getAllow() != null && !policy.getAllow().isEmpty()
                && !ToolPatterns.matchesAny(policy.getAllow(), callName)) {
            return Decision.deny("工具 " + callName + " 不在应用允许的工具列表内");
        }
        applyRiskOverride(policy.getRiskOverrides(), invocation);
        return Decision.proceed();
    }

    @Override
    public int getOrder() {
        return 200;
    }

    private void applyRiskOverride(Map<String, String> overrides, ToolInvocation invocation) {
        if (overrides == null || overrides.isEmpty() || !StringUtils.hasText(invocation.getCallName())) {
            return;
        }
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (ToolPatterns.matches(entry.getKey(), invocation.getCallName())
                    && StringUtils.hasText(entry.getValue())) {
                invocation.setRiskLevel(entry.getValue().trim());
                return;
            }
        }
    }
}
