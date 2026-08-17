package cn.cangjiecloud.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {

    /** 会话 ID */
    private String sessionId;

    /** AI 回复内容 */
    private String message;

    /** 角色 */
    private String role;

    /** 检索来源 */
    private List<Map<String, Object>> retrievalSources;

    /** 总 token 数 */
    private Integer tokens;

    /** 响应耗时（毫秒） */
    private Long duration;
}
