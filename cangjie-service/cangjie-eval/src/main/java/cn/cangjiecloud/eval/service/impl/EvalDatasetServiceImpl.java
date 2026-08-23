package cn.cangjiecloud.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.mapper.EvalDatasetMapper;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class EvalDatasetServiceImpl extends ServiceImpl<EvalDatasetMapper, EvalDatasetEntity>
        implements IEvalDatasetService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalDatasetEntity create(EvalDatasetEntity entity) {
        entity.setCaseCount(0);
        save(entity);
        log.info("评估数据集已创建: {} ({})", entity.getName(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalDatasetEntity update(String id, EvalDatasetEntity entity) {
        EvalDatasetEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("数据集不存在");
        }
        entity.setId(id);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        EvalDatasetEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        log.info("评估数据集已删除: {} ({})", entity.getName(), id);
    }

    @Override
    public List<EvalDatasetEntity> list(String keyword) {
        LambdaQueryWrapper<EvalDatasetEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(EvalDatasetEntity::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(EvalDatasetEntity::getName, keyword);
        }
        return list(wrapper);
    }
}