package cn.cangjiecloud.knowledge.mapper;

import cn.cangjiecloud.knowledge.rag.VectorUpdateItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 向量存储专用 Mapper（pgvector + pgroonga 原生 SQL）
 * <p>
 * pgvector 的相似度运算符（&lt;=&gt;）、类型转换（::vector）与 pgroonga 全文检索
 * 无法用 MyBatis-Plus Wrapper 表达，统一以注解 SQL 承载。
 */
@Mapper
public interface KnowledgeVectorMapper {

    /**
     * 单条向量回写
     */
    @Update("UPDATE knowledge_paragraph SET embedding = #{vector}::vector, vector_status = 'embedded' WHERE id = #{id}")
    int updateEmbedding(@Param("id") String id, @Param("vector") String vector);

    /**
     * 批量向量回写：UPDATE ... FROM (VALUES ...) 单语句整批，一次网络往返
     */
    @Update("<script>UPDATE knowledge_paragraph AS t SET embedding = v.vec::vector, vector_status = 'embedded' "
            + "FROM (VALUES "
            + "<foreach collection='items' item='it' separator=','>(#{it.id}, #{it.vector}::vector)</foreach>"
            + ") AS v(id, vec) WHERE t.id = v.id</script>")
    int updateEmbeddingBatch(@Param("items") List<VectorUpdateItem> items);

    /**
     * 向量相似度检索（余弦距离）
     */
    @Select("SELECT id, document_id, knowledge_base_id, content, title, "
            + "1 - (embedding <=> #{vector}::vector) AS similarity "
            + "FROM knowledge_paragraph "
            + "WHERE deleted = 0 AND knowledge_base_id = #{kbId} AND vector_status = 'embedded' "
            + "ORDER BY embedding <=> #{vector}::vector LIMIT #{topK}")
    List<Map<String, Object>> searchByVector(@Param("vector") String vector,
                                             @Param("kbId") String knowledgeBaseId,
                                             @Param("topK") int topK);

    /**
     * 全文检索（pgroonga）
     */
    @Select("SELECT id, document_id, knowledge_base_id, content, title, "
            + "pgroonga_score(tableoid, ctid) AS rank "
            + "FROM knowledge_paragraph "
            + "WHERE deleted = 0 AND knowledge_base_id = #{kbId} AND content &@~ #{query} "
            + "ORDER BY rank DESC LIMIT #{topK}")
    List<Map<String, Object>> searchByFullText(@Param("query") String query,
                                               @Param("kbId") String knowledgeBaseId,
                                               @Param("topK") int topK);

    /**
     * 清空知识库下所有向量
     */
    @Update("UPDATE knowledge_paragraph SET embedding = NULL, vector_status = 'pending' "
            + "WHERE knowledge_base_id = #{kbId}")
    int clearEmbeddingByKb(@Param("kbId") String knowledgeBaseId);

    /**
     * 清空指定文档所有向量
     */
    @Update("UPDATE knowledge_paragraph SET embedding = NULL, vector_status = 'pending' "
            + "WHERE document_id = #{documentId}")
    int clearEmbeddingByDocument(@Param("documentId") String documentId);

    /**
     * 写入文档级摘要与摘要向量
     */
    @Update("UPDATE knowledge_document SET summary = #{summary}, summary_embedding = #{vector}::vector "
            + "WHERE id = #{id}")
    int updateDocumentSummary(@Param("id") String documentId,
                              @Param("summary") String summary,
                              @Param("vector") String vector);

    /**
     * two-stage 第一阶段：按摘要向量筛选候选文档 ID
     */
    @Select("SELECT id FROM knowledge_document "
            + "WHERE deleted = 0 AND knowledge_base_id = #{kbId} AND summary_embedding IS NOT NULL "
            + "ORDER BY summary_embedding <=> #{vector}::vector LIMIT #{topK}")
    List<String> searchSummaryDocIds(@Param("vector") String vector,
                                     @Param("kbId") String knowledgeBaseId,
                                     @Param("topK") int topK);

    /**
     * two-stage 第二阶段：限定文档集合内的段落向量检索
     */
    @Select("<script>SELECT id, document_id, knowledge_base_id, content, title, "
            + "1 - (embedding &lt;=&gt; #{vector}::vector) AS similarity "
            + "FROM knowledge_paragraph "
            + "WHERE deleted = 0 AND vector_status = 'embedded' AND document_id IN "
            + "<foreach collection='docIds' item='d' open='(' separator=',' close=')'>#{d}</foreach> "
            + "ORDER BY embedding &lt;=&gt; #{vector}::vector LIMIT #{topK}</script>")
    List<Map<String, Object>> searchByDocumentIds(@Param("vector") String vector,
                                                  @Param("docIds") List<String> documentIds,
                                                  @Param("topK") int topK);

    /**
     * 按段落 ID 集合取回段落（问题路召回）
     */
    @Select("<script>SELECT id, document_id, knowledge_base_id, content, title "
            + "FROM knowledge_paragraph "
            + "WHERE deleted = 0 AND id IN "
            + "<foreach collection='ids' item='d' open='(' separator=',' close=')'>#{d}</foreach> "
            + "LIMIT #{limit}</script>")
    List<Map<String, Object>> selectByParagraphIds(@Param("ids") List<String> paragraphIds,
                                                   @Param("limit") int limit);
}
