package cn.cangjiecloud.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.knowledge.entity.KnowledgeParagraphEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeParagraphMapper;
import cn.cangjiecloud.knowledge.service.IKnowledgeParagraphService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeParagraphServiceImpl
        extends ServiceImpl<KnowledgeParagraphMapper, KnowledgeParagraphEntity>
        implements IKnowledgeParagraphService {

    @Override
    public List<KnowledgeParagraphEntity> listByDocument(String documentId) {
        return list(new LambdaQueryWrapper<KnowledgeParagraphEntity>()
                .eq(KnowledgeParagraphEntity::getDocumentId, documentId)
                .orderByAsc(KnowledgeParagraphEntity::getChunkIndex));
    }

    @Override
    public IPage<KnowledgeParagraphEntity> pageQuery(String documentId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<KnowledgeParagraphEntity> wrapper = new LambdaQueryWrapper<KnowledgeParagraphEntity>()
                .eq(KnowledgeParagraphEntity::getDocumentId, documentId)
                .orderByAsc(KnowledgeParagraphEntity::getChunkIndex);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    public List<KnowledgeParagraphEntity> listByKnowledgeBase(String knowledgeBaseId) {
        return list(new LambdaQueryWrapper<KnowledgeParagraphEntity>()
                .eq(KnowledgeParagraphEntity::getKnowledgeBaseId, knowledgeBaseId)
                .orderByAsc(KnowledgeParagraphEntity::getChunkIndex));
    }

    @Override
    public void deleteByDocument(String documentId) {
        remove(new LambdaQueryWrapper<KnowledgeParagraphEntity>()
                .eq(KnowledgeParagraphEntity::getDocumentId, documentId));
    }
}
