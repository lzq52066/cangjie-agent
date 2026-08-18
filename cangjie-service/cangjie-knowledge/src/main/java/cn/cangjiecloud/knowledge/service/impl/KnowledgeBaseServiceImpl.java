package cn.cangjiecloud.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.core.rag.SplitStrategy;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO;
import cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;
import cn.cangjiecloud.knowledge.mapper.KnowledgeBaseMapper;
import cn.cangjiecloud.knowledge.service.IKnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseEntity>
        implements IKnowledgeBaseService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity create(KnowledgeBaseCreateDTO dto) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSplitStrategy(SplitStrategy.of(dto.getSplitStrategy()).getCode());
        entity.setChunkSize(dto.getChunkSize() != null ? dto.getChunkSize() : 500);
        entity.setSeparators(dto.getSeparators());
        entity.setEmbeddingModelId(dto.getEmbeddingModelId());
        entity.setDirectory(dto.getDirectory());
        entity.setDocumentCount(0);
        entity.setParagraphCount(0);
        entity.setStatus("active");
        save(entity);
        log.info("知识库已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity update(String id, KnowledgeBaseUpdateDTO dto) {
        KnowledgeBaseEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("知识库不存在");
        }
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getDescription())) entity.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getSplitStrategy())) {
            entity.setSplitStrategy(SplitStrategy.of(dto.getSplitStrategy()).getCode());
        }
        if (dto.getChunkSize() != null) entity.setChunkSize(dto.getChunkSize());
        if (StringUtils.hasText(dto.getSeparators())) entity.setSeparators(dto.getSeparators());
        if (StringUtils.hasText(dto.getEmbeddingModelId())) entity.setEmbeddingModelId(dto.getEmbeddingModelId());
        if (StringUtils.hasText(dto.getDirectory())) entity.setDirectory(dto.getDirectory());
        updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        KnowledgeBaseEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("知识库已删除: {}", id);
    }

    @Override
    public List<KnowledgeBaseEntity> list(String keyword) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(KnowledgeBaseEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KnowledgeBaseEntity::getName, keyword)
                    .or().like(KnowledgeBaseEntity::getDescription, keyword);
        }
        return list(wrapper);
    }

    }
