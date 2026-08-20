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
}
