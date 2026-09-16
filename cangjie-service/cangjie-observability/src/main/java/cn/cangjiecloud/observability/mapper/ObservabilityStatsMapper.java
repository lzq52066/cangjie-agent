package cn.cangjiecloud.observability.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 观测面板统计 Mapper：跨表聚合查询（chat_message / operation_log）。
 * <p>
 * 这些是跨模块表的聚合统计，无法用 MyBatis-Plus Wrapper 表达，SQL 统一放在
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
}