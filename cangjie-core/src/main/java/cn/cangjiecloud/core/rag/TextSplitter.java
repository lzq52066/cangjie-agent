package cn.cangjiecloud.core.rag;

/**
 * 文本切片策略接口
 * <p>
 * 所有切片策略实现此接口，通过 {@link SplitStrategy} 枚举标识类型，
 * 由 {@link TextSplitterFactory} 根据 knowledge_base 配置动态选择。
 */
public interface TextSplitter {

    /**
     * 将原始文本切分为多个 TextChunk
     *
     * @param text       原始文本
     * @param chunkSize  目标切片大小（字符数）
     * @param overlap    相邻切片重叠量（字符数）
     * @return 切片列表
     */
    java.util.List<TextChunk> split(String text, int chunkSize, int overlap);

    /**
     * 获取切片策略类型
     */
    SplitStrategy getStrategy();
}
