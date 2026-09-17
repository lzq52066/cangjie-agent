package cn.cangjiecloud.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.prompt.entity.CommandEntity;
import cn.cangjiecloud.prompt.mapper.CommandMapper;
import cn.cangjiecloud.prompt.service.ICommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class CommandServiceImpl extends ServiceImpl<CommandMapper, CommandEntity>
        implements ICommandService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommandEntity create(CommandEntity entity) {
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        save(entity);
        log.info("命令已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommandEntity update(String id, CommandEntity entity) {
        CommandEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("命令不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        CommandEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("命令已删除: {}", id);
    }

    @Override
    public IPage<CommandEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CommandEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(CommandEntity::getCreateTime);
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(CommandEntity::getName, keyword)
                .or()
                .like(CommandEntity::getCommand, keyword)
                .or()
                .like(CommandEntity::getDescription, keyword));
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
