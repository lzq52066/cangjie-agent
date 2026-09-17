package cn.cangjiecloud.core.harness;

import cn.cangjiecloud.core.model.ChatMessage;

import java.util.List;

/**
 * 模型网关：把"同步调用"与"流式调用"收敛为一次交换。
 * <p>
 * {@code sink} 非 null 时实现方应以流式方式调用并把增量转发给 sink，同时在返回的
 * {@link AssistantTurn} 中携带完整内容与 token 统计；{@code sink} 为 null 时走同步调用。
 * 模型标识、采样参数与工具列表都从 {@link HarnessRequest} 读取，使实现方无需引擎透传零散参数。
 */
public interface ModelGateway {

    /**
     * 发起一轮模型交换
     *
     * @param request  执行输入（提供 modelId / modelName / modelSettings / tools）
     * @param messages 当前会话消息（引擎持有，实现方只读）
     * @param sink     增量回调，null 表示同步调用
     */
    AssistantTurn exchange(HarnessRequest request, List<ChatMessage> messages, DeltaSink sink);
}
