package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 回复内容 */
    private String content;

    /** 角色 */
    @Builder.Default
    private String role = "assistant";

    /** 输入 token 数 */
    private int promptTokens;

    /** 输出 token 数 */
    private int completionTokens;

    /** 总 token 数 */
    private int totalTokens;

    /** 模型名称 */
    private String model;

    /** 完成原因 */
    private String finishReason;
}
