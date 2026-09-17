package cn.cangjiecloud.core.harness;

/**
 * 特殊工具路由：函数名不对应 {@code tool} 表记录、需要平台内能力承接的工具。
 * <p>
 * 现有场景：agentic 知识库检索（search_knowledge_base）、子 Agent（agent_task）、
 * 工作流即工具（wf_&lt;id&gt;）。实现方按 {@link #getOrder()} 竞争，命中即短路，
 * 从而让 Agent 循环内部不再出现任何工具名的硬编码判断。
 */
public interface SpecialToolRoute {

    /**
     * 路由名称（用于日志与 step 留痕）
     */
    String name();

    /**
     * 是否接管该函数名
     */
    boolean matches(ToolInvocation invocation, HarnessContext context);

    /**
     * 执行并返回给模型的正文（失败时返回可读的错误文本，不抛异常）
     */
    String execute(ToolInvocation invocation, HarnessContext context);

    /**
     * 该路由暴露给模型的规格（无静态规格时返回 null）
     */
    default cn.cangjiecloud.core.tool.ToolSpecification specification(HarnessContext context) {
        return null;
    }

    default int getOrder() {
        return 100;
    }
}
