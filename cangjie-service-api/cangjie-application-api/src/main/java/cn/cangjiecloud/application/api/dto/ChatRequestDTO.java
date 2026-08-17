package cn.cangjiecloud.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequestDTO {

    /** 应用 ID */
    @NotBlank(message = "应用 ID 不能为空")
    private String applicationId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 会话 ID（可选，为空表示新会话） */
    private String sessionId;

    /** 来源：web / api / wechat / dingtalk / feishu */
    private String source = "api";

    /** 是否流式返回 */
    private Boolean stream = false;
}
