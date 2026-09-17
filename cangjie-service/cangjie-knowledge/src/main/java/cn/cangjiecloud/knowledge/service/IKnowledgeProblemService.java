package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.knowledge.api.dto.ProblemCreateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeProblemEntity;

import java.util.List;
import java.util.Map;

/**
 * 知识库问题服务：问题管理 + 问题路召回
 */
public interface IKnowledgeProblemService extends IService<KnowledgeProblemEntity> {

    IPage<KnowledgeProblemEntity> pageQuery(String knowledgeBaseId, Integer pageNum, Integer pageSize);

    /**
     * 创建问题并关联段落
     */
    KnowledgeProblemEntity create(String knowledgeBaseId, ProblemCreateDTO dto);

    KnowledgeProblemEntity update(String problemId, ProblemCreateDTO dto);

    void delete(String problemId);

    /**
     * 补充关联段落
     */
    void associate(String problemId, List<String> paragraphIds);

    /**
     * 分页查询问题的关联段落 ID
     */
    IPage<String> pageQueryParagraphIds(String problemId, Integer pageNum, Integer pageSize);

    /**
     * 问题路召回：按查询文本匹配问题，返回命中的段落 ID（附问题内容）
     *
     * @return paragraphId -> 匹配到的问题内容
     */
    Map<String, String> recallParagraphs(List<String> knowledgeBaseIds, String query, int limit);
}
