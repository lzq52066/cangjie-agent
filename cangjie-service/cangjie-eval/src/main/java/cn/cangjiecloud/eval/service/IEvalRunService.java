package cn.cangjiecloud.eval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.cangjiecloud.eval.dto.EvalRunCompareDTO;
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

    /**
     * 删除运行记录，并级联掉它名下的 case 明细
     * <p>
     * 正在执行的运行不允许删除
     */
    void deleteRun(String runId);

    /**
     * 取消运行：已排队的直接收进 cancelled，执行中的在下一个用例边界停下，
     * 不再烧剩下的 token
     *
     * @return 被取消的运行记录
     */
    EvalRunEntity cancelRun(String runId);

    /**
     * 对比两次运行：汇总指标差值 + 逐用例差值（跌得最狠的在前）。
     * <p>
     * 光比均值会把「十条关键用例跌了、总体反而涨了」这种典型回归看漏，
     * 所以逐用例的部分不可省。
     *
     * @param fromRunId 基准运行（「改动前」）
     * @param toRunId   对比运行（「改动后」）
     * @return 对比结果，两侧都只读不写
     */
    EvalRunCompareDTO compare(String fromRunId, String toRunId);

    /**
     * 同数据集中紧邻它之前的那一次已结束运行，用作「只选了一个运行」时的对比基准。
     * <p>
     * 只在同一个数据集里往前找：跨数据集比出来的差值混的是数据而不是改动，看了只会误导。
     *
     * @param runId 当前运行
     * @return 基准运行；找不到（该数据集的第一次评估）时抛业务异常而不是拿一个不相干的运行来凑
     */
    EvalRunEntity previousRun(String runId);
}