package cn.cangjiecloud.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.knowledge.entity.KnowledgeBaseEntity;

import java.util.List;

public interface IKnowledgeBaseService extends IService<KnowledgeBaseEntity> {

    KnowledgeBaseEntity create(cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseCreateDTO dto);

    KnowledgeBaseEntity update(String id, cn.cangjiecloud.knowledge.api.dto.KnowledgeBaseUpdateDTO dto);

    void delete(String id);

    List<KnowledgeBaseEntity> list(String keyword);
}
