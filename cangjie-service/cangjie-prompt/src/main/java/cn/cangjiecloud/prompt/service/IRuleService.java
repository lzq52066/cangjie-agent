package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.RuleEntity;

public interface IRuleService extends IService<RuleEntity> {

    RuleEntity create(RuleEntity entity);

    RuleEntity update(String id, RuleEntity entity);

    void delete(String id);

    IPage<RuleEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}
