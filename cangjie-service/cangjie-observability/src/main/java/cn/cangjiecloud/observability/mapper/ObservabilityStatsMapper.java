package cn.cangjiecloud.observability.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 观测面板统计 Mapper：跨表聚合查询（chat_message / operation_log / llm_trace）。
 * <p>
 * 这些是聚合统计，无法用 MyBatis-Plus Wrapper 表达（P95、按模型切片），SQL 统一放在
 * resources/mapper/ObservabilityStatsMapper.xml，Java 代码中不出现 SQL。
 */
@Mapper
public interface ObservabilityStatsMapper {

    /**
     * 指定时间之后的用户消息数（今日对话数）
     */
    Long countUserMessagesSince(@Param("start") LocalDateTime start);

    /**
     * 指定时间之后的 AI 回复数（今日模型调用数）
     */
    Long countAssistantMessagesSince(@Param("start") LocalDateTime start);

    /**
     * 操作平均耗时（毫秒）
     */
    Double avgOperationDuration();

    /**
     * 时间窗口内的模型调用汇总：调用数 / 失败数 / 成本 / token / P95 耗时与首字延迟。
     * <p>
     * P95 而不是平均值：平均值会被少数几次超时洗得很干净，而用户投诉的恰好就是那几次。
     *
     * @param start 窗口起点（含）
     * @return 单行汇总；窗口内无调用时计数为 0、分位数为 null
     */
    Map<String, Object> llmTraceSummary(@Param("start") LocalDateTime start);

    /**
     * 首字延迟汇总（只看带了 ttft 的流式调用）。
     * <p>
     * 单独一次查询而不是合进汇总：同步调用没有 ttft，混在一起时分位数会被大片 NULL 顶歪。
     */
    Map<String, Object> llmTraceTtftSummary(@Param("start") LocalDateTime start);

    /**
     * 时间窗口内按模型切片的调用汇总（成本高的排在前面）。
     * <p>
     * 只给总量回答不了「钱花在哪个模型上」，而降本必须先定位到模型。
     */
    List<Map<String, Object>> llmTraceByModel(@Param("start") LocalDateTime start, @Param("limit") int limit);

    /**
     * 在线评测的按天覆盖度：已评 / 待评各多少条。
     * <p>
     * 只算成功的调用：失败的调用没回答可评，把它们计入分母会把覆盖率永远拖低，
     * 看起来像「评测没跑」而实际是「压根不该评」。
     */
    List<Map<String, Object>> llmTraceEvalCoverage(@Param("start") LocalDateTime start);

    /**
     * 已评样本的分数原文（逐行 {@code start_time} + {@code eval_scores}）。
     * <p>
     * 指标名是动态产出的 SPI 读数，没法在 SQL 里预先展开成列，因此这里只把已经筛窄的那部分
     * （已评、窗口内、只要两列）交给 Java 汇总，行数另有上限。
     */
    List<Map<String, Object>> llmTraceScoredScores(@Param("start") LocalDateTime start,
                                                   @Param("limit") int limit);
}