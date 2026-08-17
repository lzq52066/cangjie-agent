package cn.cangjiecloud.oss.service.impl;

import cn.cangjiecloud.oss.entity.OperationLogEntity;
import cn.cangjiecloud.oss.mapper.OperationLogMapper;
import cn.cangjiecloud.oss.service.IOperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLogEntity>
        implements IOperationLogService {

    @Override
    public void record(OperationLogEntity entity) {
        if (entity == null) {
            return;
        }
        save(entity);
    }

    @Override
    public IPage<OperationLogEntity> pageQuery(String module, String action, String status,
                                               Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .eq(StringUtils.hasText(module), OperationLogEntity::getModule, module)
                .eq(StringUtils.hasText(action), OperationLogEntity::getAction, action)
                .eq(StringUtils.hasText(status), OperationLogEntity::getStatus, status)
                .orderByDesc(OperationLogEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }
}
