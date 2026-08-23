package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalCaseEntity;

import java.util.List;

public interface IEvalCaseService extends IService<EvalCaseEntity> {

    EvalCaseEntity create(EvalCaseEntity entity);

    EvalCaseEntity update(String id, EvalCaseEntity entity);

    void delete(String id);

    List<EvalCaseEntity> listByDataset(String datasetId);
}