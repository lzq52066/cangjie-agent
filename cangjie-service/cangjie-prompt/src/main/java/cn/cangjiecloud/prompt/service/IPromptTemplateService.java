package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.PromptTemplateEntity;

import java.util.List;

public interface IPromptTemplateService extends IService<PromptTemplateEntity> {

    PromptTemplateEntity create(PromptTemplateEntity entity);

    PromptTemplateEntity update(String id, PromptTemplateEntity entity);

    void delete(String id);

    List<PromptTemplateEntity> list(String keyword);
}
