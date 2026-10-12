package cn.cangjiecloud.core.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 一条 span 的完整描述：除耗时与状态外，还带上节点的输入 / 输出载荷。
 * <p>
 * 只有「这次检索耗时 12ms」是排障不够用的 —— 必须能回答「用的是什么 query、召回了哪些片段、
 * 各通道分数多少、为什么最终没进上下文」。载荷都是 JSON 文本，由实现方原样落库，
 * core 层不关心结构，避免为了可观测性把业务对象拖进抽象层。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceSpan {

    /** 模块名（如 chat / retrieval） */
    private String module;

    /** 动作（如 send / search） */
    private String action;

    /** 链路 ID */
    private String traceId;

    /** 耗时（毫秒） */
    private long duration;

    /** 状态：success / fail */
    private String status;

    /** 概要描述，列表页直接展示 */
    private String message;

    /** 输入载荷（JSON），可为空 */
    private String input;

    /** 输出载荷（JSON），可为空 */
    private String output;

    /** 附加维度（计数、耗时分解等），可为空；实现方序列化为 JSON 存列 */
    private Map<String, Object> metadata;

    /**
     * 不带载荷的便捷构造，等价于旧版六参数 {@code record(...)} 调用。
     */
    public static TraceSpan of(String module, String action, String traceId, long duration,
                               String status, String message) {
        return TraceSpan.builder()
                .module(module).action(action).traceId(traceId)
                .duration(duration).status(status).message(message)
                .build();
    }

    public boolean hasPayload() {
        return input != null || output != null || (metadata != null && !metadata.isEmpty());
    }
}
