package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;

public interface IEvalDatasetService extends IService<EvalDatasetEntity> {

    EvalDatasetEntity create(EvalDatasetEntity entity);

    EvalDatasetEntity update(String id, EvalDatasetEntity entity);

    void delete(String id);

    IPage<EvalDatasetEntity> pageQuery(String keyword, Integer pageNum, Integer pageSize);
}