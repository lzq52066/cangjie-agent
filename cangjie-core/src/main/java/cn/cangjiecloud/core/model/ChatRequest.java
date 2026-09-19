package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 模型名称（如 gpt-4o、qwen-max） */
    private String model;

    /** 对话消息列表 */
    private List<ChatMessage> messages;

    /** 温度参数（0~2） */
    @Builder.Default
    private double temperature = 0.7;

    /** 最大输出 token 数 */
    private int maxTokens;

    /** top_p 参数 */
    @Builder.Default
    private double topP = 1.0;

    /** 是否流式 */
    @Builder.Default
    private boolean stream = false;

    /** 额外参数（各模型特有） */
    private java.util.Map<String, Object> extra;

    /** 工具列表（Function Calling） */
    private java.util.List<java.util.Map<String, Object>> tools;

    /** 工具选择策略：auto / none / required */
    private String toolChoice;

    /**
     * 结构化输出的 JSON Schema 原文（根元素）。
     * 非空时要求模型严格按该 schema 返回 JSON；为空则不加约束（模型自由输出）。
     * 目前仅同步调用（{@code chat}）生效，流式调用忽略该字段。
     */
    private String responseSchema;

    /** 结构化输出 schema 名称（部分厂商 json_schema 模式要求提供） */
    private String responseSchemaName;

    /**
     * 业务上下文（traceId / 应用 / 会话 / 用户）。
     * <p>
     * 不参与模型请求体，仅透传给 langchain4j 的 ChatModelListener，
     * 使每次模型调用都能带上业务归属与真实 token usage 落库。
     */
    private ChatTraceContext traceContext;
}
