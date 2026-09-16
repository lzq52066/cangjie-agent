package cn.cangjiecloud.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeParagraphMapper extends BaseMapper<KnowledgeParagraphEntity> {

    /**
     * 向量相似度检索（pgvector cosine 距离）。
     * ::vector 类型转换与 &lt;=&gt; 运算符无法用 Wrapper 表达，SQL 见 KnowledgeParagraphMapper.xml。
     */
    List<Map<String, Object>> vectorSearch(
            @Param("queryVector") String queryVector,
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("topK") int topK);

    /**
     * 全文检索（pgroonga）。&amp;@~ 运算符无法用 Wrapper 表达，SQL 见 KnowledgeParagraphMapper.xml。
     */
    List<Map<String, Object>> fullTextSearch(
            @Param("query") String query,
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("topK") int topK);
}