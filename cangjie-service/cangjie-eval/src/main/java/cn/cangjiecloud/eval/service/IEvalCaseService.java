package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;

import java.util.List;

public interface IEvalCaseService extends IService<EvalCaseEntity> {

    EvalCaseEntity create(EvalCaseEntity entity);

    EvalCaseEntity update(String id, EvalCaseEntity entity);

    void delete(String id);

    IPage<EvalCaseEntity> pageQuery(String datasetId, Integer pageNum, Integer pageSize);

    /**
     * 全量列表（评估运行时需遍历全部用例，供 EvalRunService 使用，勿用于分页接口）
     */
    List<EvalCaseEntity> listByDataset(String datasetId);
}