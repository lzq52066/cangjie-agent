package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.CommandEntity;

public interface ICommandService extends IService<CommandEntity> {

    CommandEntity create(CommandEntity entity);

    CommandEntity update(String id, CommandEntity entity);

    void delete(String id);

    IPage<CommandEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}
