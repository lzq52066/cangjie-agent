package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.chat.harness.HarnessConfigResolver;
import cn.cangjiecloud.core.harness.HarnessConfig;
import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 人工审批钩子（order 300）：命中审批条件时以 REQUIRE_APPROVAL 挂起 run。
 * <p>
 * 触发条件（任一）：工具元信息标记 require_approval、风险等级为 high、命中应用配置的
 * {@code approval.requireFunctions}。已放行的调用（resume 回来）直接放行，避免二次挂起。
 */
@Component
@RequiredArgsConstructor
public class ApprovalHook implements ToolCallHook {

    private static final int REASON_ARGUMENTS_CHARS = 500;

    private final HarnessConfigResolver configResolver;

    @Override
    public Decision before(ToolInvocation invocation, HarnessContext context) {
        HarnessConfig config = context.getRequest() == null ? null : context.getRequest().getConfig();
        if (!configResolver.approvalEnabled(config)) {
            return Decision.proceed();
        }
        if (context.isApproved(invocation.getCallId())) {
            return Decision.proceed();
        }
        HarnessConfig.Approval approval = config == null ? null : config.getApproval();
        String riskLevel = invocation.getRiskLevel();
        boolean required = invocation.isRequireApproval()
                || "high".equalsIgnoreCase(riskLevel)
                || (approval != null && ToolPatterns.matchesAny(approval.getRequireFunctions(),
                invocation.getCallName()));
        if (!required) {
            return Decision.proceed();
        }
        return Decision.requireApproval(reason(invocation, riskLevel), riskLevel);
    }

    @Override
    public int getOrder() {
        return 300;
    }

    private String reason(ToolInvocation invocation, String riskLevel) {
        String arguments = invocation.getArgumentsJson();
        if (arguments != null && arguments.length() > REASON_ARGUMENTS_CHARS) {
            arguments = arguments.substring(0, REASON_ARGUMENTS_CHARS) + "...";
        }
        return "高风险工具调用需要审批：" + invocation.getCallName()
                + "（风险等级：" + (riskLevel == null ? "medium" : riskLevel) + "）"
                + (arguments == null ? "" : "\n入参：" + arguments);
    }
}
