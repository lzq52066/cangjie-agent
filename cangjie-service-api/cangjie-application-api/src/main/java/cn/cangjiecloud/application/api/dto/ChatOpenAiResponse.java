package cn.cangjiecloud.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 兼容 /v1/chat/completions 响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatOpenAiResponse {

    /** 响应 ID */
    private String id;

    /** 对象类型 */
    @Builder.Default
    private String object = "chat.completion";

    /** 创建时间戳 */
    private Long created;

    /** 模型名称 */
    private String model;

    /** 选项列表 */
    private List<Choice> choices;

    /** Token 用量 */
    private Usage usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /** 序号 */
        private Integer index;

        /** 消息 */
        private ChoiceMessage message;

        /** 流式增量 */
        private ChoiceMessage delta;

        /** 结束原因：stop / length / content_filter */
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceMessage {
        /** 角色 */
        private String role;

        /** 内容 */
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /** 提示词 token 数 */
        private Integer promptTokens;

        /** 补全 token 数 */
        private Integer completionTokens;

        /** 总 token 数 */
        private Integer totalTokens;
    }
}