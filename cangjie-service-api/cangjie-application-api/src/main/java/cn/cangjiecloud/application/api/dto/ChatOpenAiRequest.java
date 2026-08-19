package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容 /v1/chat/completions 请求格式
 */
@Data
public class ChatOpenAiRequest {

    /** 模型名称（映射到应用的 apikey 或 modelName） */
    private String model;

    /** 消息列表 */
    @NotEmpty(message = "messages 不能为空")
    private List<Message> messages;

    /** 是否流式返回 */
    private Boolean stream = false;

    /** 温度参数 */
    private Double temperature;

    /** 最大 token 数 */
    private Integer maxTokens;

    /** 用户标识（可选，用于记录和长期记忆） */
    private String user;

    @Data
    public static class Message {
        /** 角色：system / user / assistant */
        private String role;
        /** 消息内容 */
        private String content;
    }
}