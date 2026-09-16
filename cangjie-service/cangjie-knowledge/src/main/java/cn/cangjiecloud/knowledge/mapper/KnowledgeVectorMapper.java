package cn.cangjiecloud.knowledge.mapper;

import cn.cangjiecloud.knowledge.rag.VectorUpdateItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 向量存储专用 Mapper（pgvector + pgroonga 原生 SQL）。
 * <p>
 * pgvector 的相似度运算符（&lt;=&gt;）、类型转换（::vector）与 pgroonga 全文检索
 * 无法用 MyBatis-Plus Wrapper 表达，SQL 统一放在
 * resources/mapper/KnowledgeVectorMapper.xml，Java 代码中不出现 SQL。
 */
@Mapper
public interface KnowledgeVectorMapper {

    /**
     * 单条向量回写
     */
    int updateEmbedding(@Param("id") String id, @Param("vector") String vector);

    /**
     * 批量向量回写：UPDATE ... FROM (VALUES ...) 单语句整批，一次网络往返
     */
    int updateEmbeddingBatch(@Param("items") List<VectorUpdateItem> items);

    /**
     * 向量相似度检索（余弦距离）
     */
    List<Map<String, Object>> searchByVector(@Param("vector") String vector,
                                             @Param("kbId") String knowledgeBaseId,
                                             @Param("topK") int topK);

    /**
     * 全文检索（pgroonga）
     */
    List<Map<String, Object>> searchByFullText(@Param("query") String query,
                                               @Param("kbId") String knowledgeBaseId,
                                               @Param("topK") int topK);

    /**
     * 清空知识库下所有向量
     */
    int clearEmbeddingByKb(@Param("kbId") String knowledgeBaseId);

    /**
     * 清空指定文档所有向量
     */
    int clearEmbeddingByDocument(@Param("documentId") String documentId);

    /**
     * 写入文档级摘要与摘要向量
     */
    int updateDocumentSummary(@Param("id") String documentId,
                              @Param("summary") String summary,
                              @Param("vector") String vector);

    /**
     * two-stage 第一阶段：按摘要向量筛选候选文档 ID
     */
    List<String> searchSummaryDocIds(@Param("vector") String vector,
                                     @Param("kbId") String knowledgeBaseId,
                                     @Param("topK") int topK);

    /**
     * two-stage 第二阶段：限定文档集合内的段落向量检索
     */
    List<Map<String, Object>> searchByDocumentIds(@Param("vector") String vector,
                                                  @Param("docIds") List<String> documentIds,
                                                  @Param("topK") int topK);

    /**
     * 按段落 ID 集合取回段落（问题路召回）
     */
    List<Map<String, Object>> selectByParagraphIds(@Param("ids") List<String> paragraphIds,
                                                   @Param("limit") int limit);
}