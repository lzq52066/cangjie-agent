package cn.cangjiecloud.core.harness;

/**
 * 流式增量回调。为 null 时模型网关走同步调用，非 null 时走流式调用，
 * 从而使同步对话与流式对话共用同一套 Agent 循环。
 */
@FunctionalInterface
public interface DeltaSink {

    /**
     * 收到一段内容增量
     */
    void onDelta(String delta);

    DeltaSink NOOP = delta -> {
    };
}
