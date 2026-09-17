package cn.cangjiecloud.chat.harness.hook;

import cn.cangjiecloud.core.harness.HarnessContext;
import cn.cangjiecloud.core.harness.ToolCallHook;
import cn.cangjiecloud.core.harness.ToolInvocation;
import cn.cangjiecloud.core.tool.ToolSpecification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 参数校验钩子（order 100，最先执行）。
 * <p>
 * 依据已装配给模型的工具规格做必填校验：校验失败以 DENY 表达，原因回喂模型，
 * 让模型有机会补齐参数后重试，而不是让整轮对话报错。
 */
@Component
public class ArgumentValidationHook implements ToolCallHook {

    @Override
    public Decision before(ToolInvocation invocation, HarnessContext context) {
        if (!StringUtils.hasText(invocation.getCallName())) {
            return Decision.deny("工具调用缺少函数名");
        }
        Map<String, Object> arguments = invocation.getArguments();
        ToolSpecification spec = findSpec(context, invocation.getCallName());
        if (spec == null || spec.getParameters() == null) {
            return Decision.proceed();
        }
        for (String required : requiredParams(spec)) {
            if (arguments == null || isBlank(arguments.get(required))) {
                return Decision.deny("参数校验失败：缺少必填参数 " + required);
            }
        }
        return Decision.proceed();
    }

    @Override
    public int getOrder() {
        return 100;
    }

    private ToolSpecification findSpec(HarnessContext context, String callName) {
        if (context.getRequest() == null || context.getRequest().getTools() == null) {
            return null;
        }
        for (ToolSpecification spec : context.getRequest().getTools()) {
            if (spec != null && callName.equalsIgnoreCase(spec.getName())) {
                return spec;
            }
        }
        return null;
    }

    private List<String> requiredParams(ToolSpecification spec) {
        Object required = spec.getParameters().get("required");
        if (!(required instanceof List<?> list)) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                names.add(String.valueOf(item));
            }
        }
        return names;
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && !StringUtils.hasText(text));
    }
}
