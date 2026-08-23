package cn.cangjiecloud.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeParagraphMapper extends BaseMapper<KnowledgeParagraphEntity> {

    /**
     * 向量相似度检索（pgvector cosine 距离）
     * 使用 ::vector 类型转换，参数为 pgvector 的字符串表示
     */
    @Select("""
            SELECT p.*, 1 - (p.embedding <=> CAST(#{queryVector} AS vector)) AS similarity
            FROM knowledge_paragraph p
            WHERE p.deleted = 0
              AND p.knowledge_base_id = #{knowledgeBaseId}
              AND p.vector_status = 'embedded'
            ORDER BY p.embedding <=> CAST(#{queryVector} AS vector)
            LIMIT #{topK}
            """)
    List<java.util.Map<String, Object>> vectorSearch(
            @Param("queryVector") String queryVector,
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("topK") int topK);

    /**
     * 全文检索（pgroonga）
     */
    @Select("""
            SELECT p.*,
                   pgroonga_score(p.tableoid, p.ctid) AS rank
            FROM knowledge_paragraph p
            WHERE p.deleted = 0
              AND p.knowledge_base_id = #{knowledgeBaseId}
              AND p.content &@~ #{query}
            ORDER BY rank DESC
            LIMIT #{topK}
            """)
    List<java.util.Map<String, Object>> fullTextSearch(
            @Param("query") String query,
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("topK") int topK);
}
