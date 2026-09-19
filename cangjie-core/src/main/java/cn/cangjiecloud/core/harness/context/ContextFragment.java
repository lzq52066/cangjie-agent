package cn.cangjiecloud.core.harness.context;

import cn.cangjiecloud.core.model.ChatMessage;
import cn.cangjiecloud.core.model.TokenEstimator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 上下文片段：某个槽位产出的一段消息，以及它的预算元数据。
 * <p>
 * 留痕时只记录 slot、来源与估算 token，不回写全部正文，避免 step 表写放大。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextFragment {

    private ContextSlot slot;

    /** 产出该片段的后端标识（如 promptTemplate / rag / memory / history） */
    private String source;

    private List<ChatMessage> messages;

    /** 估算 token（jtokkit cl100k_base 精确计数） */
    private int estTokens;

    /** 本片段是否可被压缩或丢弃 */
    @Builder.Default
    private boolean compressible = true;

    /** 被预算裁剪时的丢弃比例（0~1，用于观测） */
    private double droppedRatio;

    /** 预算分配给本槽位的额度（0 表示未参与裁剪） */
    private int budgetTokens;

    /** 因超预算被丢弃的 token 估算值 */
    private int droppedTokens;

    public static ContextFragment of(ContextSlot slot, String source, List<ChatMessage> messages) {
        return ContextFragment.builder()
                .slot(slot)
                .source(source)
                .messages(messages)
                .compressible(slot.compressible())
                .build();
    }

    /**
     * 空片段（contributor 无内容时返回，便于统一留痕）
     */
    public static ContextFragment empty(ContextSlot slot, String source) {
        return ContextFragment.builder()
                .slot(slot)
                .source(source)
                .messages(List.of())
                .estTokens(0)
                .compressible(slot.compressible())
                .build();
    }

    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }

    public int size() {
        return messages == null ? 0 : messages.size();
    }

    /**
     * 按 jtokkit（cl100k_base）重算 token，含工具调用参数。
     */
    public void refreshEstTokens() {
        this.estTokens = TokenEstimator.count(messages);
    }
}
