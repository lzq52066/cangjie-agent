package cn.cangjiecloud.core.model;

/**
 * 模型单价来源端口（成本估算用）。
 * <p>
 * 由模型模块实现、可观测模块消费：可观测性不依赖模型模块，编译期只认这个接口，
 * 运行期按 {@code @Autowired(required = false)} 注入 —— 与本仓库既有的跨模块可选注入
 * （{@code TraceCollector} / {@code EmbeddingProvider} / {@code ConversationSource}）同一种接法。
 * <p>
 * 之所以不让 trace 写入方直接查 {@code model_config}：那会把模型表的列名与主键规则钉进可观测侧，
 * 单价改成按厂商维护或改成阶梯价时，链路写入就得跟着改。
 */
public interface ModelPricingSource {

    /**
     * 取模型单价。
     *
     * @param modelId 模型 ID（{@code model_config.id}），可为 null
     * @return 模型不存在或没维护单价时返回 null；调用方按「成本不估算」处理，不要当 0
     */
    ModelPricing findPricing(String modelId);
}
