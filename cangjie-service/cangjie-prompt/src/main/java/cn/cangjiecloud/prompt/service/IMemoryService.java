package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.MemoryEntity;

import java.util.List;

public interface IMemoryService extends IService<MemoryEntity> {

    MemoryEntity create(MemoryEntity entity);

    MemoryEntity update(String id, MemoryEntity entity);

    void delete(String id);

    List<MemoryEntity> list(String keyword);
}
