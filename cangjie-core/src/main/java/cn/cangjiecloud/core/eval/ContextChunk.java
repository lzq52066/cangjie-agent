package cn.cangjiecloud.core.eval;

/**
 * 送入指标计算的一个检索片段。
 * <p>
 * 只带指标真正用得上的三样：正文、段落 ID、文档 ID。评估指标之所以认这个类型而不是
 * {@code RetrievalResult}，是为了让在线评测（没有各通道融合分数、只有历史召回正文）
 * 能用同一套指标实现 —— 否则同一个 faithfulness 要写两遍。
 *
 * @param text        片段正文
 * @param paragraphId 段落（chunk）ID，片段级指标靠它和 ground truth 对齐，可能为空
 * @param documentId  文档 ID，文档级指标靠它对齐，可能为空
 */
public record ContextChunk(String text, String paragraphId, String documentId) {
}
