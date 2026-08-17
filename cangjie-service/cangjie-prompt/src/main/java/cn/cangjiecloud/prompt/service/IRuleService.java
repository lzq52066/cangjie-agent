package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.RuleEntity;

import java.util.List;

public interface IRuleService extends IService<RuleEntity> {

    RuleEntity create(RuleEntity entity);

    RuleEntity update(String id, RuleEntity entity);

    void delete(String id);

    List<RuleEntity> list(String keyword);
}
