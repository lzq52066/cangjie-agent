package cn.cangjiecloud.core.harness.context;

import org.springframework.core.Ordered;

/**
 * 上下文贡献者（SPI）。
 * <p>
 * 每个实现只负责一个槽位（提示词、检索、记忆、历史…），由 {@code ContextPipeline} 按
 * {@link Ordered#getOrder()} 汇总，替代原先集中在一个方法里的命令式拼接。
 */
public interface ContextContributor extends Ordered {

    /**
     * 贡献者标识（留痕用）
     */
    String name();

    /**
     * 归属槽位
     */
    ContextSlot slot();

    /**
     * 是否参与本次装配
     */
    default boolean supports(ContextRequest request) {
        return true;
    }

    /**
     * 产出片段；无内容时返回 {@link ContextFragment#empty}
     */
    ContextFragment contribute(ContextRequest request);

    /**
     * 默认排在系统消息之后、历史之前
     */
    @Override
    default int getOrder() {
        return slot().order() * 100;
    }
}
