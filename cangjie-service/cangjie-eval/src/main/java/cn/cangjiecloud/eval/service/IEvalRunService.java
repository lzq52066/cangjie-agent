package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.entity.EvalRunEntity;

import java.util.Map;

public interface IEvalRunService extends IService<EvalRunEntity> {

    /**
     * 发起评估运行
     *
     * @param datasetId 数据集 ID
     * @param config    运行配置（modelId / rerank / topK 等）
     * @return 运行记录
     */
    EvalRunEntity run(String datasetId, Map<String, Object> config);

    /**
     * 获取运行报告
     */
    EvalRunEntity getReport(String runId);
}