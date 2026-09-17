package cn.cangjiecloud.prompt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.prompt.entity.MemoryEntity;

public interface IMemoryService extends IService<MemoryEntity> {

    MemoryEntity create(MemoryEntity entity);

    MemoryEntity update(String id, MemoryEntity entity);

    void delete(String id);

    IPage<MemoryEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}
