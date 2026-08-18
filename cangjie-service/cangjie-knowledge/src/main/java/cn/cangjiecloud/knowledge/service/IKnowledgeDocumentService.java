package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.knowledge.entity.KnowledgeDocumentEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IKnowledgeDocumentService extends IService<KnowledgeDocumentEntity> {

    /**
     * 上传并处理文档
     */
    KnowledgeDocumentEntity upload(String knowledgeBaseId, MultipartFile file);

    /**
     * 删除文档及其段落和向量
     */
    void delete(String documentId);

    /**
     * 重新处理文档（仅失败状态）
     */
    void reprocess(String documentId);

    /**
     * 重新向量化文档（不限制状态，对已有段落重新生成向量）
     */
    void reEmbed(String documentId);

    /**
     * 查询知识库下的文档列表
     */
    List<KnowledgeDocumentEntity> listByKnowledgeBase(String knowledgeBaseId);
}
