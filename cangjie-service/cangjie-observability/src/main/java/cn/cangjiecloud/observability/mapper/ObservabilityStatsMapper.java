package cn.cangjiecloud.observability.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 观测面板统计 Mapper：跨表聚合查询（chat_message / operation_log）
 */
@Mapper
public interface ObservabilityStatsMapper {

    /**
     * 指定时间之后的用户消息数（今日对话数）
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE role = 'user' AND deleted = 0 AND create_time >= #{start}")
    Long countUserMessagesSince(@Param("start") LocalDateTime start);

    /**
     * 指定时间之后的 AI 回复数（今日模型调用数）
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE role = 'assistant' AND deleted = 0 AND create_time >= #{start}")
    Long countAssistantMessagesSince(@Param("start") LocalDateTime start);

    /**
     * 操作平均耗时（毫秒）
     */
    @Select("SELECT COALESCE(AVG(duration), 0)::float8 FROM operation_log WHERE deleted = 0")
    Double avgOperationDuration();
}
