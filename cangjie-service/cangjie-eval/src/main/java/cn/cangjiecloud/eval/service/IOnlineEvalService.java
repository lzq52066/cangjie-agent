package cn.cangjiecloud.eval.service;

import cn.cangjiecloud.eval.dto.OnlineEvalRequestDTO;
import cn.cangjiecloud.eval.dto.OnlineEvalSummaryDTO;

/**
 * 在线评测服务：对线上真实调用抽样打分，让 RAGAS 那套指标不止跑在离线数据集上。
 * <p>
 * 离线评估回答「改动前水位多少」，只有线上抽样能回答「现在用户拿到的水位是多少」——
 * 检索调参、换模型、知识库新增内容，最先出问题的都是线上而不是评测集。
 */
public interface IOnlineEvalService {

    /**
     * 评一批待标注的线上调用（同步执行，一轮几百条以内）。
     *
     * @param request 参数，可整体为 null 走默认
     * @return 本轮执行摘要
     */
    OnlineEvalSummaryDTO score(OnlineEvalRequestDTO request);

    /**
     * 当前待评积压条数（没有接入调用记录来源时为 0）。
     */
    long pendingCount();
}
