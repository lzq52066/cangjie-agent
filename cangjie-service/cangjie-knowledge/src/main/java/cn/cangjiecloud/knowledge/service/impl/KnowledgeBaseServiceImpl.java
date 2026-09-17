package cn.cangjiecloud.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.constant.AppConst;
import cn.cangjiecloud.common.context.UserContext;
import cn.cangjiecloud.common.domain.UserIdentity;
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

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseEntity>
        implements IKnowledgeBaseService {

    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_PRIVATE = "private";

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
        entity.setVisibility(normalizeVisibility(dto.getVisibility(), VISIBILITY_PRIVATE));
        entity.setDocumentCount(0);
        entity.setParagraphCount(0);
        entity.setStatus("active");
        save(entity);
        log.info("知识库已创建: {} ({}), visibility={}", entity.getName(), entity.getId(), entity.getVisibility());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity update(String id, KnowledgeBaseUpdateDTO dto) {
        KnowledgeBaseEntity entity = getById(id);
        if (entity == null) {
            throw new ApiException("知识库不存在");
        }
        checkAccessEntity(entity);
        if (StringUtils.hasText(dto.getName())) entity.setName(dto.getName());
        if (StringUtils.hasText(dto.getDescription())) entity.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getSplitStrategy())) {
            entity.setSplitStrategy(SplitStrategy.of(dto.getSplitStrategy()).getCode());
        }
        if (dto.getChunkSize() != null) entity.setChunkSize(dto.getChunkSize());
        if (StringUtils.hasText(dto.getSeparators())) entity.setSeparators(dto.getSeparators());
        if (StringUtils.hasText(dto.getEmbeddingModelId())) entity.setEmbeddingModelId(dto.getEmbeddingModelId());
        if (StringUtils.hasText(dto.getDirectory())) entity.setDirectory(dto.getDirectory());
        if (StringUtils.hasText(dto.getVisibility())) {
            entity.setVisibility(normalizeVisibility(dto.getVisibility(), entity.getVisibility()));
        }
        updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        KnowledgeBaseEntity entity = getById(id);
        if (entity == null) return;
        checkAccessEntity(entity);
        removeById(id);
        log.info("知识库已删除: {}", id);
    }

    @Override
    public IPage<KnowledgeBaseEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(KnowledgeBaseEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KnowledgeBaseEntity::getName, keyword)
                    .or().like(KnowledgeBaseEntity::getDescription, keyword));
        }
        // 数据权限：管理员可见全部；普通用户仅可见公开库与自己创建的库
        if (!isAdmin()) {
            String userId = UserContext.getUserId();
            wrapper.and(w -> w.eq(KnowledgeBaseEntity::getVisibility, VISIBILITY_PUBLIC)
                    .or().eq(KnowledgeBaseEntity::getCreateBy, userId));
        }
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    public void checkAccess(String knowledgeBaseId) {
        if (isAdmin()) {
            return;
        }
        KnowledgeBaseEntity entity = getById(knowledgeBaseId);
        checkAccessEntity(entity);
    }

    /**
     * 数据权限校验：库不存在/私有且非创建者 → 拒绝
     */
    private void checkAccessEntity(KnowledgeBaseEntity entity) {
        if (entity == null) {
            throw new ApiException("知识库不存在");
        }
        if (isAdmin()) {
            return;
        }
        if (VISIBILITY_PUBLIC.equals(entity.getVisibility())) {
            return;
        }
        String userId = UserContext.getUserId();
        if (userId == null || !userId.equals(entity.getCreateBy())) {
            throw new ApiException("无权访问该知识库: " + entity.getName());
        }
    }

    private boolean isAdmin() {
        UserIdentity identity = UserContext.getIdentity();
        return identity != null && AppConst.ROLE_ADMIN.equalsIgnoreCase(identity.getRole());
    }

    private String normalizeVisibility(String visibility, String fallback) {
        if (VISIBILITY_PUBLIC.equalsIgnoreCase(visibility)) return VISIBILITY_PUBLIC;
        if (VISIBILITY_PRIVATE.equalsIgnoreCase(visibility)) return VISIBILITY_PRIVATE;
        return StringUtils.hasText(fallback) ? fallback : VISIBILITY_PRIVATE;
    }
}
