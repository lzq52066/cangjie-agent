package cn.cangjiecloud.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.cangjiecloud.prompt.entity.LongTermMemoryEntity;
import cn.cangjiecloud.prompt.entity.MemorySimilarity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 长期记忆 Mapper：
 * 通用 CRUD 走 MyBatis-Plus {@link BaseMapper}；向量近邻查询/回写因涉及 pgvector 语法，使用 XML 自定义 SQL。
 */
@Mapper
public interface LongTermMemoryMapper extends BaseMapper<LongTermMemoryEntity> {

    /**
     * 向量近邻检索：在同一用户 + 应用 + 记忆类型下按余弦距离取最相似的激活记忆。
     * 仅比较相同 embedding_dim 的向量（不同维度向量不可运算）；
     * scene 类型额外限定同一会话，user 类型不限定会话。
     *
     * @param vector        查询向量（"[1.0,0.2,...]" 字符串形式）
     * @param dim           向量维度
     * @param userId        用户 ID
     * @param applicationId 应用 ID
     * @param memoryType    记忆类型（user / scene）
     * @param dimension     记忆维度（preference/background/convention/goal），不同维度不判重
     * @param sessionId     会话 ID（scene 类型生效）
     * @param limit         返回条数
     */
    List<MemorySimilarity> searchSimilar(@Param("vector") String vector,
                                         @Param("dim") int dim,
                                         @Param("userId") String userId,
                                         @Param("applicationId") String applicationId,
                                         @Param("memoryType") String memoryType,
                                         @Param("dimension") String dimension,
                                         @Param("sessionId") String sessionId,
                                         @Param("limit") int limit);

    /**
     * 回写记忆向量
     */
    int updateEmbedding(@Param("id") String id,
                        @Param("vector") String vector,
                        @Param("dim") int dim);
}
