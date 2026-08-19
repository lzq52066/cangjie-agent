package cn.cangjiecloud.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式对话的分片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatChunk {

    /** 本次推送的内容片段 */
    private String delta;

    /** 是否结束 */
    private boolean done;

    /** 完成原因（done=true 时有值） */
    private String finishReason;

    /** 错误信息（发生异常时设置） */
    private String error;
}
