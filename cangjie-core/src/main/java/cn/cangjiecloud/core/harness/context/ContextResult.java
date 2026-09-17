package cn.cangjiecloud.core.harness.context;

import cn.cangjiecloud.core.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文管线产出
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextResult {

    /** 最终会话消息（按槽位顺序拼装、已裁剪） */
    private List<ChatMessage> messages;

    /** 各槽位明细（留痕与观测用） */
    private List<ContextFragment> fragments;

    /** 估算总 token */
    private int estTokens;

    /** 是否发生过预算裁剪 */
    private boolean trimmed;

    public static ContextResult of(List<ChatMessage> messages) {
        return ContextResult.builder()
                .messages(messages == null ? new ArrayList<>() : messages)
                .fragments(new ArrayList<>())
                .build();
    }
}
