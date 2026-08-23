package cn.cangjiecloud.core.rag;

import java.util.List;

/**
 * 检索结果重排序器
 * <p>
 * 在混合检索粗排（向量+全文融合）后，用更强模型对候选结果精排，
 * 提升检索精度。典型场景：粗排 topK*3 → 精排 topK。
 */
public interface Reranker {

    /** 类型标识：none / bge / cohere */
    String getType();

    /**
     * 对候选结果重排序
     *
     * @param query       用户查询文本
     * @param candidates  粗排候选结果（已融合，未截断）
     * @param topK        期望返回条数
     * @return 精排后的结果（按分数降序，数量不超过 topK）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK);
}