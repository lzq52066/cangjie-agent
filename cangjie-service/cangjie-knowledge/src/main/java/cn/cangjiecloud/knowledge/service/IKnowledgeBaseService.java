package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;

public interface IKnowledgeBaseService extends IService<KnowledgeBaseEntity> {

    KnowledgeBaseEntity create(cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO dto);

    KnowledgeBaseEntity update(String id, cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO dto);

    void delete(String id);

    /**
     * 分页查询知识库列表（含数据权限：非管理员仅可见公开库与本人创建的库）
     */
    IPage<KnowledgeBaseEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 校验当前用户对知识库的访问权限（管理员放行；私有库仅创建者可访问），
     * 无权限时抛出业务异常
     */
    void checkAccess(String knowledgeBaseId);
}
