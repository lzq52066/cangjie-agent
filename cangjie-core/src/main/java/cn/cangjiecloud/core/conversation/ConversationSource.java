package cn.cangjiecloud.core.conversation;

import java.util.List;

/**
 * 对话样本来源端口（评测回流用）。
 * <p>
 * 由对话模块实现、评测模块消费：评测模块不依赖对话模块，编译期只认这个接口，
 * 运行期按 {@code @Autowired(required = false)} 注入 —— 与本仓库既有的跨模块可选注入
 * （{@code TraceCollector} / {@code EmbeddingProvider}）保持同一种接法。
 * <p>
 * 之所以不做成「评测模块直接查 chat_message」：那会把对话表的列名与问答配对规则钉死在
 * 评测侧，会话结构一改版（比如加多轮改写、工具调用消息）评测就悄悄拿错轮次。
 */
public interface ConversationSource {

    /**
     * 取一条对话样本。
     *
     * @param messageId 消息 ID：AI 消息直接用；用户消息则向后找它那一轮的回答
     * @return 取不到（消息不存在、不是可回流的 role）时返回 null，不抛异常
     */
    ConversationSample loadSample(String messageId);

    /**
     * 按条件捞候选样本，按消息时间倒序。
     *
     * @param filter 筛选条件，null 视为不限
     * @return 至多 {@code filter.limit} 条；没有候选时返回空列表
     */
    List<ConversationSample> searchSamples(ConversationFilter filter);
}
