package cn.cangjiecloud.core.harness.context;

/**
 * 上下文槽位。
 * <p>
 * 声明式地划分一次会话消息的构成，取代散落在 {@code buildContext} /
 * {@code injectLongTermMemory} 里的命令式拼接顺序。枚举顺序即默认装配顺序。
 */
public enum ContextSlot {

    /** 前置系统消息（如场景/摘要注入，必须位于首位） */
    SYSTEM_PRE(0, false),

    /** 提示词模板（人设与指令） */
    SYSTEM(1, false),

    /** 检索增强内容（知识库片段） */
    RAG(2, true),

    /** 长期记忆 */
    MEMORY(3, true),

    /** 多轮历史 */
    HISTORY(4, true),

    /** 工具结果（run 内部追加，装配阶段一般不产出） */
    TOOL_RESULT(5, true),

    /** 本轮用户输入（始终保留） */
    USER(6, false);

    /** 默认装配顺序 */
    private final int order;

    /** 超预算时是否允许压缩/丢弃 */
    private final boolean compressible;

    ContextSlot(int order, boolean compressible) {
        this.order = order;
        this.compressible = compressible;
    }

    public int order() {
        return order;
    }

    public boolean compressible() {
        return compressible;
    }

    /**
     * 配置里使用的键（小写下划线，与 {@code slotRatios} 对应）
     */
    public String key() {
        return name().toLowerCase();
    }

    public static ContextSlot of(String value) {
        if (value == null) {
            return null;
        }
        for (ContextSlot slot : values()) {
            if (slot.name().equalsIgnoreCase(value.trim()) || slot.key().equalsIgnoreCase(value.trim())) {
                return slot;
            }
        }
        return null;
    }
}
