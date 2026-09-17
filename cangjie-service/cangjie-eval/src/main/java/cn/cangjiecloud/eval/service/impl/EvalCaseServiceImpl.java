package cn.cangjiecloud.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.cangjiecloud.common.exception.ApiException;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;
import cn.cangjiecloud.eval.mapper.EvalCaseMapper;
import cn.cangjiecloud.eval.service.IEvalCaseService;
import cn.cangjiecloud.eval.service.IEvalDatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalCaseServiceImpl extends ServiceImpl<EvalCaseMapper, EvalCaseEntity>
        implements IEvalCaseService {

    private final IEvalDatasetService datasetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseEntity create(EvalCaseEntity entity) {
        if (!StringUtils.hasText(entity.getDatasetId())) {
            throw new ApiException("数据集 ID 不能为空");
        }
        save(entity);
        // 更新数据集用例计数
        updateCaseCount(entity.getDatasetId());
        log.info("评估用例已创建: {} ({})", entity.getQuestion(), entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvalCaseEntity update(String id, EvalCaseEntity entity) {
        EvalCaseEntity existing = getById(id);
        if (existing == null) {
            throw new ApiException("用例不存在");
        }
        entity.setId(id);
        entity.setDatasetId(existing.getDatasetId());
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        EvalCaseEntity entity = getById(id);
        if (entity == null) return;
        removeById(id);
        updateCaseCount(entity.getDatasetId());
        log.info("评估用例已删除: {}", id);
    }

    @Override
    public IPage<EvalCaseEntity> pageQuery(String datasetId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<EvalCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCaseEntity::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalCaseEntity::getCreateTime);
        return page(new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
    }

    @Override
    public List<EvalCaseEntity> listByDataset(String datasetId) {
        LambdaQueryWrapper<EvalCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalCaseEntity::getDatasetId, datasetId);
        wrapper.orderByAsc(EvalCaseEntity::getCreateTime);
        return list(wrapper);
    }

    private void updateCaseCount(String datasetId) {
        long count = count(new LambdaQueryWrapper<EvalCaseEntity>()
                .eq(EvalCaseEntity::getDatasetId, datasetId));
        EvalDatasetEntity ds = new EvalDatasetEntity();
        ds.setId(datasetId);
        ds.setCaseCount((int) count);
        datasetService.updateById(ds);
    }
}