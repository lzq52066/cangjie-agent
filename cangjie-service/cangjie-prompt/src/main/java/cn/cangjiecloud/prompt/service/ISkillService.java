package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.SkillEntity;

import java.util.List;

public interface ISkillService extends IService<SkillEntity> {

    SkillEntity create(SkillEntity entity);

    SkillEntity update(String id, SkillEntity entity);

    void delete(String id);

    List<SkillEntity> list(String keyword);
}
