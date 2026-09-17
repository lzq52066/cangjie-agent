package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;

public interface IPromptTemplateService extends IService<PromptTemplateEntity> {

    PromptTemplateEntity create(PromptTemplateEntity entity);

    PromptTemplateEntity update(String id, PromptTemplateEntity entity);

    void delete(String id);

    IPage<PromptTemplateEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}
