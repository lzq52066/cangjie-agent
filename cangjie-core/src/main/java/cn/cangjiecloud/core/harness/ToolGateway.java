package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.tool.ToolSpecification;

import java.util.List;

/**
 * 工具网关：所有工具调用的唯一入口。
 * <p>
 * 职责依次为：函数名解析 → 参数校验 → 策略判定（Hook 链）→ 特殊路由（检索/子 Agent/工作流）
 * → 实际执行（带超时）→ 输出截断 → 留痕。对话 Agent、工作流节点、子 Agent 都必须经由此入口，
 * 以保证埋点与权限的一致性。
 */
public interface ToolGateway {

    /**
     * 执行一次工具调用（内部已包含拒绝/超时处理，不抛异常，结果通过 status 表达）
     */
    ToolOutcome invoke(ToolInvocation invocation, HarnessContext context);

    /**
     * 解析函数名对应的工具元信息（不存在时返回 empty）
     */
    List<ToolSpecification> resolveSpecs(List<String> toolIds);
}
