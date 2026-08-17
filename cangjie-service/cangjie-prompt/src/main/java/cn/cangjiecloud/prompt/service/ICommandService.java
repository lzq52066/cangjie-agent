package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.CommandEntity;

import java.util.List;

public interface ICommandService extends IService<CommandEntity> {

    CommandEntity create(CommandEntity entity);

    CommandEntity update(String id, CommandEntity entity);

    void delete(String id);

    List<CommandEntity> list(String keyword);
}
