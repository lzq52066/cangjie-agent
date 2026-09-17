package cn.cangjiecloud.core.harness.context;

/**
 * 上下文装配管线（SPI，由 cangjie-service 侧实现）。
 * <p>
 * 把"如何拼出一份会话消息"从对话实现里剥离出来：管线负责按序调度
 * {@link ContextContributor} 并按 {@link ContextBudget} 裁剪，对话只消费结果。
 */
public interface ContextPipeline {

    /**
     * 装配一次上下文
     */
    ContextResult assemble(ContextRequest request);
}
