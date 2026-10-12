package cn.cangjiecloud.core.eval;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 一次指标计算的输入样本。
 * <p>
 * 离线评测填满各字段；在线评测（对历史对话打分）拿不到期望回答与参考文档，
 * 那几个字段留空即可 —— 「缺什么就不出什么分」是各 {@link EvalMetric} 自己的职责，
 * 接口层不做「补 0」这种会把水位算歪的事。
 *
 * @param query            用户问题
 * @param answer           待评的回答（离线为生成回答，在线为线上真实回答）
 * @param contexts         本次用到的检索片段，按最终名次排序（第 0 个是第 1 名）
 * @param referenceDocs    参考文档 ID 集合（文档级 ground truth），可为空
 * @param referenceChunks  参考片段 ID 集合（片段级 ground truth），可为空
 * @param expectedAnswer   期望回答，无标注时为空
 */
public record EvalSample(String query,
                         String answer,
                         List<ContextChunk> contexts,
                         List<String> referenceDocs,
                         List<String> referenceChunks,
                         String expectedAnswer) {

    public EvalSample {
        // List.copyOf 不容 null：ground truth 是从 JSON 文本解出来的，脏一条就整条 case 报错不合规
        contexts = contexts == null ? List.of()
                : contexts.stream().filter(Objects::nonNull).toList();
        referenceDocs = nonBlank(referenceDocs);
        referenceChunks = nonBlank(referenceChunks);
    }

    private static List<String> nonBlank(Collection<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && !id.isBlank()).toList();
    }

    /** 片段的纯文本列表（多数指标只关心正文） */
    public List<String> contextTexts() {
        return contexts.stream().map(ContextChunk::text).filter(t -> t != null && !t.isBlank()).toList();
    }

    /** 有没有可供判定的检索片段 */
    public boolean hasContexts() {
        return !contextTexts().isEmpty();
    }

    /** 有没有 ground truth（文档级或片段级任一即可） */
    public boolean hasGroundTruth() {
        return !referenceDocs.isEmpty() || !referenceChunks.isEmpty();
    }
}
