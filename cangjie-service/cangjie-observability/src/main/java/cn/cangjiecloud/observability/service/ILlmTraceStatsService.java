package cn.cangjiecloud.observability.service;

import cn.cangjiecloud.observability.dto.LlmTraceStatsDTO;
import cn.cangjiecloud.observability.dto.OnlineEvalTrendDTO;

/**
 * 模型调用统计服务：给观测面板提供 P95 / 错误率 / 成本口径，以及在线评测的水位与趋势。
 */
public interface ILlmTraceStatsService {

    /**
     * 最近 N 个自然日（含今天）的模型调用统计。
     *
     * @param days 天数，非正数按 1 天，上限 365 天（窗口再大这个聚合就只是看起来能跑）
     * @return 窗口内的汇总，永不返回 null；没有调用时各项计数为 0
     */
    LlmTraceStatsDTO stats(int days);

    /**
     * 最近 N 个自然日的在线评测趋势：评了多少、还剩多少没评、各指标均值按天怎么走。
     *
     * @param days 天数，窗口口径与 {@link #stats(int)} 一致
     * @return 趋势，永不返回 null；一条都没评时计数为 0、指标表为空，而不是伪造一个均值
     */
    OnlineEvalTrendDTO onlineEvalTrend(int days);
}
