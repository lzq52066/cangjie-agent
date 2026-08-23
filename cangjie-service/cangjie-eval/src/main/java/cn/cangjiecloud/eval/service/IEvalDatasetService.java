package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalDatasetEntity;

import java.util.List;

public interface IEvalDatasetService extends IService<EvalDatasetEntity> {

    EvalDatasetEntity create(EvalDatasetEntity entity);

    EvalDatasetEntity update(String id, EvalDatasetEntity entity);

    void delete(String id);

    List<EvalDatasetEntity> list(String keyword);
}