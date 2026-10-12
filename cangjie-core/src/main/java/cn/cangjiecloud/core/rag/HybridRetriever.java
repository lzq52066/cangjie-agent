package cn.cangjiecloud.core.rag;

import java.util.List;

/**
 * 混合检索器接口
 * <p>
 * 同时执行向量相似度检索与全文检索两路，融合后排序（可再叠一层重排序）。
 * 支持 simple（段落级直接检索）与 two_stage（摘要→段落两级检索）两种模式。
 * <p>
 * 唯一的抽象方法是 {@link #retrieve(String, RetrievalOptions)}：其余重载都是便捷入口，
 * 只把自己认识的字段填进 {@link RetrievalOptions}，未填的由实现方回落到全局/知识库配置。
 * 这样调用方少传参数不会「悄悄换掉检索口径」，多传参数也不新增重载。
 */
public interface HybridRetriever {

    /**
     * 混合检索（兼容旧接口：不过滤、检索口径取默认）
     * <p>
     * knowledgeBaseId 允许为 null（语义就是「没有可检索的库」），这里不直接用 List.of，
     * 否则便捷方法会因为一个实现方本就能处理的空值抛出 NPE。
     */
    default List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK) {
        return retrieve(query, RetrievalOptions.builder()
                .knowledgeBaseIds(knowledgeBaseId == null ? List.of() : List.of(knowledgeBaseId))
                .topK(topK).build());
    }

    /**
     * 混合检索（兼容旧接口：不过滤、检索口径取默认）
     */
    default List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK) {
        return retrieve(query, RetrievalOptions.builder()
                .knowledgeBaseIds(knowledgeBaseIds).topK(topK).build());
    }

    /**
     * 混合检索（带相似度阈值）
     *
     * @param similarityThreshold 相似度阈值，只作用于向量通道（0.0 表示不过滤）
     */
    default List<RetrievalResult> retrieve(String query, String knowledgeBaseId, int topK, double similarityThreshold) {
        return retrieve(query, RetrievalOptions.builder()
                .knowledgeBaseIds(knowledgeBaseId == null ? List.of() : List.of(knowledgeBaseId))
                .topK(topK).similarityThreshold(similarityThreshold).build());
    }

    /**
     * 混合检索（多知识库，带相似度阈值）
     */
    default List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK, double similarityThreshold) {
        return retrieve(query, RetrievalOptions.builder()
                .knowledgeBaseIds(knowledgeBaseIds).topK(topK)
                .similarityThreshold(similarityThreshold).build());
    }

    /**
     * 混合检索（指定检索模式）
     *
     * @param searchMode {@link RetrievalOptions#MODE_SIMPLE} / {@link RetrievalOptions#MODE_TWO_STAGE}
     */
    default List<RetrievalResult> retrieve(String query, List<String> knowledgeBaseIds, int topK,
                                           double similarityThreshold, String searchMode) {
        return retrieve(query, RetrievalOptions.builder()
                .knowledgeBaseIds(knowledgeBaseIds).topK(topK)
                .similarityThreshold(similarityThreshold).searchMode(searchMode).build());
    }

    /**
     * 混合检索：完整口径。
     *
     * @param query   查询文本
     * @param options 检索口径（知识库范围、topK、阈值、模式、融合与重排等），null 字段取默认
     * @return 融合（并可能重排）后的检索结果，按最终分数降序，条数不强制凑满 topK
     */
    List<RetrievalResult> retrieve(String query, RetrievalOptions options);
}
