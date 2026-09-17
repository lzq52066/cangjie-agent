package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.SkillEntity;

public interface ISkillService extends IService<SkillEntity> {

    SkillEntity create(SkillEntity entity);

    SkillEntity update(String id, SkillEntity entity);

    void delete(String id);

    IPage<SkillEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}
