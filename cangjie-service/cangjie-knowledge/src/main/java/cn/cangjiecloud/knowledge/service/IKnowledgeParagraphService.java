package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;

import java.util.List;

public interface IKnowledgeParagraphService extends IService<KnowledgeParagraphEntity> {

    /**
     * 查询文档下的段落列表
     */
    List<KnowledgeParagraphEntity> listByDocument(String documentId);

    /**
     * 查询知识库下的段落列表
     */
    List<KnowledgeParagraphEntity> listByKnowledgeBase(String knowledgeBaseId);

    /**
     * 删除文档下所有段落
     */
    void deleteByDocument(String documentId);
}
