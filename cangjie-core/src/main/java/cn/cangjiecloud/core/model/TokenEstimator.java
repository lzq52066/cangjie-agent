package cn.cangjiecloud.core.model;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.List;

/**
 * 基于 jtokkit（cl100k_base BPE 词表）的 token 计数器。
 * <p>
 * 替代原先 {@code 字符数 × 0.75} 的粗估：该系数源自英文经验值，对中文严重偏低
 * （汉字通常每字 1~2 个 token），会导致上下文预算失真、历史截断不及时。
 * <p>
 * 编码实例线程安全（jtokkit 内部为不可变词表），懒加载避免无关单测付出词表加载成本。
 */
public final class TokenEstimator {

    /** OpenAI gpt-3.5/4 系列通用编码，也是绝大多数 OpenAI 兼容厂商的近似口径 */
    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private TokenEstimator() {
    }

    /**
     * 统计单段文本的 token 数（按普通文本处理，不展开特殊 token）。
     */
    public static int count(String text) {
        return text == null || text.isEmpty() ? 0 : ENCODING.countTokensOrdinary(text);
    }

    /**
     * 统计一条聊天消息的 token 数，包含正文与工具调用参数。
     */
    public static int count(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        int sum = count(message.getContent());
        if (message.getToolCalls() != null) {
            for (ChatMessage.ToolCallRef toolCall : message.getToolCalls()) {
                if (toolCall != null) {
                    sum += count(toolCall.getName()) + count(toolCall.getArguments());
                }
            }
        }
        return sum;
    }

    /**
     * 统计一组消息的 token 总和。
     */
    public static int count(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (ChatMessage message : messages) {
            sum += count(message);
        }
        return sum;
    }
}
